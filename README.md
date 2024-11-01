StateChart 
======================

Kotlin implementation of HFSM(Hierarchy Finite State Machine)


# How to use

```kotlin
// 1. define Events
class AtoB : Event()
class BtoA : Event()
class SubB1toB2 : Event()
class SubB2toB1 : Event()

// 2. define States
private val topA = object : State<Unit, Unit>("topA") {
    override fun onEnter(param: Unit) {

    }

    override fun onExit(reason: Reason) {

    }
}

private val topB = object : SubState<Unit, Unit, Unit>("topB") {
    override fun onEnter(param: Unit) {

    }

    override fun onExit(reason: Reason) {

    }
}

private val subB1 = object : State<Unit, Unit>("subB1") {
    override fun onEnter(param: Unit) {

    }

    override fun onExit(reason: Reason) {

    }
}

private val subB2 = object : State<Unit, Unit>("subB2") {
    override fun onEnter(param: Unit) {

    }

    override fun onExit(reason: Reason) {

    }

    @HandleEvent
    fun handleExitSubB(event: TriggerExitSubB): Boolean {
        sendEvent(BtoA())
        return true
    }
}

// 3. build state chart
val stateChart = StateChart.Builder<Unit>("Top")
    .eventLogger { println(it) }
    .context { }
    .initialState(topA) {}
    .state(topB) { _,_ ->
        // sub state chart
        StateChart.Builder<Unit>("Sub")
            .eventLogger { println(it) }
            .context { Unit }
            .initialState(subB1) { }
            .state(subB2)
            .transition(
                "SubB1toB2",
                setOf(subB1),
                subB2,
                SubB1toB2::class.java
            ) { _, _, _ ->
                Result.Allow(Unit)
            }.transition(
                "SubB2toB1",
                setOf(subB2),
                subB1,
                SubB2toB1::class.java
            ) { _,_,_ ->
                Result.Allow(Unit)
            }
    }
    .transition(
        "AtoB",
        setOf(topA),
        topB,
        AtoB::class.java
    ) { _, _, _ ->
        Result.Allow(Unit)
    }.transition(
        "BtoA",
        setOf(topB),
        topA,
        BtoA::class.java
    ) { _,_,_ ->
        Result.Allow(Unit)
    }.build()

// 4. start state chart
stateChart.run()

// 5. send events
stateChart.sendEvent(AtoB())
stateChart.sendEvent(SubB1toB2())
stateChart.sendEvent(TriggerExitSubB())
```