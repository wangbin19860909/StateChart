package com.benny.library.statechart

import org.jetbrains.annotations.TestOnly
import java.util.LinkedList

class StateStack private constructor(
  private val name: String,
  private val contextFactory: () -> Any,
  private val eventLogger: EventLogger,
  private val states: Map<State<*, *>, Pair<Class<out Event>, Class<out Event>>>,
) {
  private val thread = Thread.currentThread()

  private val eventStates = mutableMapOf<Class<out Event>, State<*, *>>()
  private val enteredStates = ArrayList<State<*, *>>()

  @TestOnly
  internal fun isEntered(state: State<*, *>): Boolean {
    return enteredStates.contains(state)
  }

  private val context = contextFactory()

  private var processingTask = false
  private val taskQueue = LinkedList<()->Boolean>()

  init {
    states.forEach { (state, events) ->
      eventStates[events.first] = state
      eventStates[events.second] = state
    }
  }

  private fun checkThread() {
    check(thread == Thread.currentThread()) {
      "StateChart[$name] thread[${Thread.currentThread()}] not same as original thread[$thread]"
    }
  }

  fun sendEvent(event: Event): Boolean {
    checkThread()
    return enqueueEvent(event)
  }

  private fun enqueueEvent(event: Event): Boolean {
    eventLogger.logEvent("StateQueue[$name] send event: ${event.javaClass.simpleName}")
    return enqueueTask {
      // handle event first, but ignore return value
      // top state should handle firstly
      enteredStates.asReversed().forEach {
        if (it.handleEvent(event)) {
          eventLogger.logEvent("StateQueue[$name] event: ${event.javaClass.simpleName} handled by $it")
          return@enqueueTask true
        }
      }

      val state = eventStates[event.javaClass]
      if (state == null) {
        eventLogger.logEvent("StateQueue[$name] no state found for event: ${event.javaClass.simpleName}")
        return@enqueueTask false
      }

      val triggerEvents = states[state] as Pair<Class<out Event>, Class<out Event>>
      val isEnter = triggerEvents.first == event.javaClass
      val isStateEntered = enteredStates.contains(state)
      if (isEnter && !isStateEntered) {
        // do enter
        state.enter(event, context)
        enteredStates.add(state)
        eventLogger.logEvent("StateQueue[$name] $state enter by event: ${event.javaClass.simpleName}")
        return@enqueueTask true
      } else if (!isEnter && isStateEntered) {
        // do exit
        state.exit(Reason.Default)
        enteredStates.remove(state)
        eventLogger.logEvent("StateQueue[$name] $state exit by event: ${event.javaClass.simpleName}")
        return@enqueueTask true
      } else {
        eventLogger.logEvent("StateQueue[$name] event: ${event.javaClass.simpleName} discard")
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

  class Builder<Context : Any>(private val name: String) {
    private val states =
      mutableMapOf<State<*, *>, Pair<Class<out Event>, Class<out Event>>>()

    private var eventLogger: EventLogger = EventLogger { }
    private var contextFactory: (()->Context)? = null

    fun eventLogger(logger: EventLogger): Builder<Context> = apply {
      this.eventLogger = logger
    }

    /**
     * context of state chart, can be used for store or share data among states
     */
    fun context(contextFactory: ()->Context): Builder<Context> = apply {
      this.contextFactory = contextFactory
    }

    fun <Enter : Event, Exit : Event> state(
      state: State<Enter, Context>,
      enter: Class<Enter>,
      exit: Class<Exit>
    ): Builder<Context> = apply {
      states[state] = enter to exit
    }

    inline fun <reified Enter : Event, reified Exit : Event> state(
      state: State<Enter, Context>
    ): Builder<Context> = apply {
      state(state, Enter::class.java, Exit::class.java)
    }

    fun build(): StateStack {
      return StateStack(name, contextFactory as ()->Any, eventLogger, states)
    }
  }
}