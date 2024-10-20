package com.benny.library.statechart

import java.lang.reflect.Method
import java.lang.reflect.Modifier

open class Event
open class Reason

internal interface EventDispatcher {
    fun sendEvent(event: Event)
}

open class State<Param: Any, Context: Any>(internal val name: String) {
    private var exiting = false

    @Volatile
    private var context: Context? = null
    @Volatile
    internal var eventDispatcher: EventDispatcher? = null
    // get event handle method use reflection
    // 1. public
    // 2. only one parameter and type should be inherit from Event
    // 3. return boolean
    private val eventHandlers: Map<Class<Event>, Method> by lazy {
        javaClass.declaredMethods.filter {
            Modifier.isPublic(it.modifiers)
                    && it.parameterCount == 1
                    && Event::class.java.isAssignableFrom(it.parameterTypes.first())
                    && it.returnType == Boolean::class.java
        }.associateBy {
            @Suppress("UNCHECKED_CAST")
            it.parameterTypes.first() as Class<Event>
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal open fun enter(dispatcher: EventDispatcher, param: Any, context: Any) {
        this.eventDispatcher = dispatcher
        this.context = context as Context
        onEnter(param as Param)
    }

    internal open fun exit(reason: Reason) {
        exiting = true

        onExit(reason)
        this.context = null
        this.eventDispatcher = null

        exiting = false
    }

    internal fun handleEvent(event: Event): Boolean {
        return eventHandlers[event.javaClass]?.invoke(this, event) as? Boolean
            ?: false
    }

    protected open fun sendEvent(event: Event) {
        check(!exiting) {
            "can't send event[$event] when state exiting!"
        }
        eventDispatcher?.sendEvent(event)
    }

    protected open fun onEnter(param: Param) { }
    protected open fun onExit(reason: Reason) { }

    @Suppress("UNCHECKED_CAST")
    fun <T: Context> contextAs(): T {
        return context as T
    }

    override fun toString(): String {
        return name
    }

    internal open fun toUmlNode(): String {
        val umlBuilder = StringBuilder()
        eventHandlers.forEach { (event, method) ->
            umlBuilder.append("$name : ${method.name}(${event.simpleName})")
        }
        return umlBuilder.toString()
    }
}

open class SubState<Param: Any, Context: Any, SubContext: Any>(
    name: String
) : State<Param, Context>(name) {
    internal lateinit var subChartBuilderFactory: (()->Param, ()->Context)-> StateChart.Builder<SubContext>

    private var subChart: StateChart? = null

    @Suppress("UNCHECKED_CAST")
    override fun enter(dispatcher: EventDispatcher, param: Any, context: Any) {
        subChart = subChartBuilderFactory({ param as Param }, {context as Context}).build()
        subChart?.onDiscardEvent = { discardEvent ->
            // when sub chart can not handle this event, dispatch it to parent chart
            dispatcher.sendEvent(discardEvent)
        }
        // subState should send event to subChart firstly
        super.enter(subChart!!, param, context)
        // parent state end, then sub chart state
        subChart?.run()
    }

    override fun exit(reason: Reason) {
        // sub chart exit, then parent state
        subChart?.release(reason)
        super.exit(reason)
        subChart = null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: SubContext> subContextAs(): T {
        return subChart?.context as T
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun toUmlNode(): String {
        val umlBuilder = StringBuilder()
        umlBuilder.append("state $name {").append("\n")
        subChartBuilderFactory({ null as Param }, {null as Context})
            .build()
            .toUmlDiagram()
            .let {
                val removeUmlTag = it.substring(
                    it.indexOfFirst { it == '\n' } + 1,
                    it.indexOfLast { it == '\n' }
                )
                umlBuilder.append(removeUmlTag).append("\n")
            }
        umlBuilder.append("}")
        umlBuilder.append(super.toUmlNode())
        return umlBuilder.toString()
    }
}

