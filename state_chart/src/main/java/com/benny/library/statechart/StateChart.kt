package com.benny.library.statechart

import org.jetbrains.annotations.TestOnly
import java.util.LinkedList

fun interface EventLogger {
  fun logEvent(message: String)
}

class StateChart private constructor(
  private val name: String,
  private val contextFactory: ()->Any,
  private val eventLogger: EventLogger,
  private val states: Set<State<*, *>>,
  private val transitions: Map<Class<out Event>, List<Transition<*, *, *>>>,
) {
  private object Uninitialized : State<Unit, Any>("Uninitialized")

  private class Init: Event()

  private enum class Lifecycle {
    Init, Running, Released
  }

  private val thread = Thread.currentThread()
  private var lifecycle = Lifecycle.Init

  private var processingTask = false
  private val taskQueue = LinkedList<()->Boolean>()

  internal var parent: StateChart? = null

  internal lateinit var context: Any
  private var currentState: State<*, *> = Uninitialized

  @TestOnly
  internal fun state() = currentState

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

    // Init should always handled by self
    enqueueEvent(Init())
  }

  fun release(reason: Reason) {
    checkThread()

    if (lifecycle == Lifecycle.Running) {
      currentState.exit(reason)
      taskQueue.clear()
      lifecycle = Lifecycle.Released
    }
  }

  fun sendEvent(event: Event): Boolean {
    checkThread()

    check (lifecycle == Lifecycle.Running) {
      "StateChart[$name] not initialized, call run first"
    }

    return parent?.sendEvent(event) ?: enqueueEvent(event)
  }

  internal fun enqueueEvent(event: Event): Boolean {
    eventLogger.logEvent("StateChart[$name] send event: ${event.javaClass.simpleName}")

    return enqueueTask {
      if (currentState.handleEvent(event)) {
        // current state consume first
        eventLogger.logEvent("StateChart[$name] event: ${event.javaClass.simpleName} handled by $currentState")
        return@enqueueTask true
      }

      // if current state does not consume, find match transition
      val transition = transitions[event.javaClass]?.find {
        it.from.contains(currentState)
      }

      if (transition == null) {
        eventLogger.logEvent("StateChart[$name] event: ${event.javaClass.simpleName} discard")
        return@enqueueTask false
      }

      val result = transition.transit(event, currentState, context)
      if (result is Result.Allow) {
        val state = transition.to
        eventLogger.logEvent("StateChart[$name] transition[$transition] from $currentState to $state")

        currentState.exit(result.reason)
        currentState._stateChart = null
        state._stateChart = this
        state.enter(result.param, context)
        currentState = state

        return@enqueueTask true
      } else {
        eventLogger.logEvent("StateChart[$name] event: $event disallowed")
        return@enqueueTask false
      }
    }
  }

  private fun enqueueTask(task: ()->Boolean): Boolean {
    taskQueue.offerLast(task)

    var processed = false
    if (!processingTask) {
      processingTask = true

      do {
        val nextTask = taskQueue.pollFirst()
        val result = nextTask.invoke()
        if (nextTask == task) {
          processed = result
        }
      } while (taskQueue.isNotEmpty())

      processingTask = false
    }
    return processed
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
      transitions.values.flatten().filter { it.from.contains(state) }.forEach { transition ->
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

  class Builder<Context: Any>(private val name: String) {
    private val states = LinkedHashSet<State<*, *>>()

    private var eventLogger: EventLogger = EventLogger { }
    private var contextFactory: (()->Context)? = null
    private val transitions = LinkedHashMap<Class<out Event>, MutableList<Transition<*, *, out Any>>>()

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
      transitions[Init::class.java] = mutableListOf(
        Transition(
          "init",
          setOf(Uninitialized as State<*, Context>),
          state,
          Init::class.java,
        ) { _, _, _ ->
          Result.Allow(defaultParam())
        }
      )
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
    fun <EV: Event, T: Any> transition(
      name: String,
      from: Set<State<*, Context>>,
      to: State<T, Context>,
      event: Class<EV>,
      condition: (EV, State<*, Context>, Context)-> Result<T>
    ): Builder<Context> = apply {
      from.forEach {
        check (states.contains(it)) {
          "unknown state: $it"
        }
      }

      transitions.getOrPut(event){
        mutableListOf()
      }.add(Transition(name, from, to, event, condition))
    }

    fun build(): StateChart {
      return StateChart(
        name, contextFactory as ()->Any, eventLogger, states, transitions
      )
    }
  }
}