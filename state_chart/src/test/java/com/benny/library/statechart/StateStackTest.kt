package com.benny.library.statechart

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StateStackTest {
  class EnterA() : Event()
  class ExitA: Event()

  class AProgress(val progress: Int): Event()

  class EnterB : Event()
  class ExitB: Event()

  class EnterC: Event()

  private val stateA = object : State<EnterA, Unit>("StateA") {
    override fun onEnter(param: EnterA) {

    }

    override fun onExit(reason: Reason) {

    }

    @HandleEvent
    fun handleProgress(event: AProgress) {
      throw RuntimeException("eventHandled")
    }
  }

  private val stateB = object : State<EnterB, Unit>("StateB") {
    override fun onEnter(param: EnterB) {

    }

    override fun onExit(reason: Reason) {

    }
  }

  @Test
  fun test_stateQueue_sendEvent() {
    val stateStack = StateStack.Builder<Unit>("StateQueueTest")
      .context {  }
      .eventLogger { println(it) }
      .state<EnterA, ExitA>(stateA)
      .state<EnterB, ExitB>(stateB)
      .build()

    stateStack.sendEvent(EnterA())
    assertTrue(stateStack.isEntered(stateA))

    stateStack.sendEvent(EnterB())
    assertTrue(stateStack.isEntered(stateB))
    stateStack.sendEvent(ExitA())
    assertTrue(!stateStack.isEntered(stateA))
  }

  @Test
  fun test_stateQueue_handleEvent() {
    val stateStack = StateStack.Builder<Unit>("StateQueueTest")
      .context {  }
      .eventLogger { println(it) }
      .state<EnterA, ExitA>(stateA)
      .state<EnterB, ExitB>(stateB)
      .build()

    stateStack.sendEvent(AProgress(1))

    stateStack.sendEvent(EnterA())
    assertThrows(RuntimeException::class.java) {
      stateStack.sendEvent(AProgress(100))
    }
  }
}