package com.benny.library.statechart

import java.util.LinkedList

fun interface EventLogger {
  fun logEvent(message: String)
}

class StateChart private constructor(
  internal val name: String,
  private val contextFactory: ()->Any,
  private val eventLogger: EventLogger,
  private val states: Set<State<*, *>>,
  private val transitions: Map<Class<out Event>, Transition<*, *, *>>,
): EventDispatcher {
  private object Uninitialized : State<Unit, Any>("Uninitialized")

  private class Init: Event()

  private enum class Lifecycle {
    Init, Running, Released
  }

  private val thread = Thread.currentThread()
  private var lifecycle = Lifecycle.Init

  private var processingEvent = false
  private val eventQueue = LinkedList<Event>()

  internal lateinit var context: Any
  private var currentState: State<*, *> = Uninitialized

  internal var onDiscardEvent: (Event) -> Unit = { }

  private fun checkThread() {
    check(thread == Thread.currentThread()) {
      "StateChart[$name] thread[${Thread.currentThread()}] not same as original thread[$thread]"
    }
  }

  fun run() {
    checkThread()

    check (lifecycle == Lifecycle.Init) {
      "StateChart[$name] already started"
    }

    lifecycle = Lifecycle.Running
    context = contextFactory.invoke()
    sendEvent(Init())
  }

  fun release(reason: Reason) {
    checkThread()

    if (lifecycle == Lifecycle.Running) {
      currentState.exit(reason)
      eventQueue.clear()
      lifecycle = Lifecycle.Released
    }
  }

  override fun sendEvent(event: Event) {
    checkThread()

    check (lifecycle == Lifecycle.Running) {
      "StateChart[$name] not initialized, call run first"
    }

    enqueueEvent(event, onDiscardEvent)
  }

  /**
   * print state chart transitions uml diagram
   * just copy it to [https://plantuml.com/plantuml/duml]
   */
  fun toUmlDiagram(): String {
    val umlBuilder = StringBuilder()
    umlBuilder.append("@startuml").append("\n")

    states.forEach { state ->
      umlBuilder.append("state ${state.name}").append("\n")
      val nodeUml = state.toUmlNode()
      if (nodeUml.isNotEmpty()) {
        umlBuilder.append(nodeUml).append("\n")
      }
    }

    states.forEachIndexed { index, state ->
      if (index == 0) {
        umlBuilder.append("[*] --> ${state.name}").append("\n")
      }

      val toStateMap = mutableMapOf<State<*, *>, MutableList<String>>()
      transitions.values.filter { it.from.contains(state) }.forEach { transition ->
        toStateMap.getOrPut(transition.to) {
          mutableListOf()
        }.add("${transition.name}(${transition.eventClass.simpleName})")
      }

      toStateMap.forEach { (toState, actions) ->
        umlBuilder.append("${state.name} --> ${toState.name} : ")
          .append(actions.joinToString (" / ") { it })
          .append("\n")
      }
    }
    umlBuilder.append("@enduml")
    return umlBuilder.toString()
  }

  internal fun enqueueEvent(event: Event, onDiscard: (Event)->Unit) {
    eventLogger.logEvent("StateChart[$name] send event: ${event.javaClass.simpleName}")

    if (!processingEvent) {
      processingEvent = true

      eventQueue.add(event)

      do {
        val nextEvent = eventQueue.pollFirst()!!

        if (currentState.handleEvent(nextEvent)) {
          // current state consume first
          eventLogger.logEvent("StateChart[$name] event: ${nextEvent.javaClass.simpleName} handled by $currentState")
          continue;
        }

        // if current state does not consume, find match transition
        val transition = transitions[nextEvent.javaClass]
        if (transition == null) {
          eventLogger.logEvent("StateChart[$name] event: ${nextEvent.javaClass.simpleName} discard")
          // no transition found for this event, notify outside, dispatch to parent if exist
          onDiscard(nextEvent)
          continue
        }

        val result = transition.transit(nextEvent, currentState, context)
        if (result is Result.Allow) {
          val state = transition.to
          eventLogger.logEvent("StateChart[$name] transition[$transition] from $currentState to $state")

          currentState.exit(result.reason)
          state.enter(this, result.param, context)

          currentState = state
        } else {
          eventLogger.logEvent("StateChart[$name] event: $nextEvent disallowed")
        }
      }
      while (eventQueue.isNotEmpty())

      processingEvent = false
    } else {
      eventQueue.addLast(event)
    }
  }

  class Builder<Context: Any>(private val name: String) {
    private val states = LinkedHashSet<State<*, *>>()

    private var eventLogger: EventLogger = EventLogger { }
    private var contextFactory: (()->Context)? = null
    private val transitions = LinkedHashMap<Class<out Event>, Transition<*, *, out Any>>()

    fun eventLogger(logger: EventLogger): Builder<Context> = apply {
      this.eventLogger = logger
    }

    /**
     * context of state chart, can be used for store or share data among states
     */
    fun context(contextFactory: ()->Context): Builder<Context> = apply {
      this.contextFactory = contextFactory
    }

    /**
     * initial state, when state chart run, enter immediately
     * @param defaultParam specify initial param, because there no transition for initial state
     */
    fun <T: Any> initialState(
      state: State<T, Context>,
      defaultParam: ()->T
    ): Builder<Context> = apply {
      states.add(state)

      @Suppress("UNCHECKED_CAST")
      transitions[Init::class.java] = Transition(
        "init",
        setOf(Uninitialized as State<*, Context>),
        state,
        Init::class.java,
      ) { _, _ ->
        Result.Allow(defaultParam())
      }
    }

    /**
     * define a state
     */
    fun <T: Any> state(state: State<T, Context>): Builder<Context> = apply {
      // make sure initial state always the first element
      check(states.isNotEmpty()) {
        "please call initialState before state"
      }
      states.add(state)
    }

    /**
     * define a subChart state
     * @param subChartBuilderFactory subChart builder, will receive parent state param and parent context as input
     */
    fun <T: Any, SubContext: Any> state(
      subState: SubState<T, Context, SubContext>,
      subChartBuilderFactory: (()->T, ()->Context)-> Builder<SubContext>
    ): Builder<Context> = apply {
      subState.subChartBuilderFactory = subChartBuilderFactory
      states.add(subState)
    }

    /**
     * define transition for states
     * @param event event class which trigger this transition
     * @param condition transition should be done if return Result.Allow else not.
     *                  also convert event to state param in it
     */
    fun <T: Any, EV: Event> transition(
      name: String,
      from: Set<State<*, Context>>,
      to: State<T, Context>,
      event: Class<EV>,
      condition: (EV, Context)-> Result<T>
    ): Builder<Context> = apply {
      from.forEach {
        check (states.contains(it)) {
          "unknown state: $it"
        }
      }

      transitions[event] = Transition(name, from, to, event, condition)
    }

    fun build(): StateChart {
      return StateChart(
        name, contextFactory as ()->Any, eventLogger, states, transitions
      )
    }
  }
}