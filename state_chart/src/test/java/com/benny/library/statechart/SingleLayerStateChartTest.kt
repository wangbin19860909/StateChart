package com.benny.library.statechart

import com.benny.library.statechart.Result
import org.junit.Test

import org.junit.Assert.*

import com.benny.library.statechart.State

class SingleLayerStateChartTest {
    class GreenCountDown(val duration: Int = Int.MAX_VALUE) : Event()
    class RedCountDown: Event()
    class YellowCountDown: Event()
    class Forbidden(val forbidden: Boolean) : Event()

    private val redLight = object : State<Long, Unit>("red_light") {
        override fun onEnter(param: Long) {

        }

        override fun onExit(reason: Reason) {

        }
    }

    private val greenLight = object : State<Long, Unit>("green_light") {
        override fun onEnter(param: Long) {

        }

        override fun onExit(reason: Reason) {

        }

        @HandleEvent
        fun forbid(event: Forbidden): Boolean {
            return !event.forbidden
        }
    }

    private val yellowLight = object : State<Long, Unit>("yellow_light") {
        override fun onEnter(param: Long) {

        }

        override fun onExit(reason: Reason) {

        }
    }

    @Test
    fun test_stateChart_transition() {
        val stateChart = com.benny.library.statechart.StateChart.Builder<Unit>("DockManager")
            .eventLogger { println(it) }
            .context { Unit }
            .initialState(greenLight) { 30000 }
            .state(yellowLight)
            .transition(
                "yellow_to_red",
                setOf(yellowLight),
                greenLight,
                YellowCountDown::class.java
            ) { _, _, _ ->
                Result.Allow(10000)
            }.transition(
                "yellow_to_green",
                setOf(greenLight),
                yellowLight,
                GreenCountDown::class.java
            ) { _,_,_ ->
                Result.Allow(5000)
            }.transition(
                "yellow_to_green",
                setOf(greenLight),
                yellowLight,
                Forbidden::class.java
            ) { _,_,_ ->
                Result.Allow(5000)
            }.build()

        println(stateChart.toUmlDiagram())
        stateChart.run()

        assertEquals(stateChart.state(), greenLight)
        stateChart.sendEvent(GreenCountDown())
        assertEquals(stateChart.state(), yellowLight)
        stateChart.sendEvent(GreenCountDown())
        assertEquals(stateChart.state(), yellowLight)
        stateChart.sendEvent(YellowCountDown())
        assertEquals(stateChart.state(), greenLight)
    }

    @Test
    fun test_stateChart_transition_condition() {
        val stateChart = com.benny.library.statechart.StateChart.Builder<Unit>("DockManager")
            .eventLogger { println(it) }
            .context { Unit }
            .initialState(greenLight) { 30000 }
            .state(yellowLight)
            .transition(
                "yellow_to_red",
                setOf(yellowLight),
                greenLight,
                YellowCountDown::class.java
            ) { _, _, _ ->
                Result.Allow(10000)
            }.transition(
                "yellow_to_green",
                setOf(greenLight),
                yellowLight,
                GreenCountDown::class.java
            ) { event,_,_ ->
                if (event.duration > 100) {
                    Result.Allow(5000)
                } else {
                    Result.Disallow()
                }
            }.transition(
                "yellow_to_green",
                setOf(greenLight),
                yellowLight,
                Forbidden::class.java
            ) { _,_,_ ->
                Result.Allow(5000)
            }.build()

        println(stateChart.toUmlDiagram())
        stateChart.run()

        stateChart.sendEvent(GreenCountDown(100))
        assertEquals(stateChart.state(), greenLight)

        stateChart.sendEvent(GreenCountDown(101))
        assertEquals(stateChart.state(), yellowLight)
    }

    @Test
    fun test_stateChart_handleEvent() {
        val stateChart = com.benny.library.statechart.StateChart.Builder<Unit>("DockManager")
            .eventLogger { println(it) }
            .context { Unit }
            .initialState(greenLight) { 30000 }
            .state(yellowLight)
            .transition(
                "yellow_to_red",
                setOf(yellowLight),
                greenLight,
                YellowCountDown::class.java
            ) { _, _, _ ->
                Result.Allow(10000)
            }.transition(
                "forbidden_green",
                setOf(greenLight),
                yellowLight,
                Forbidden::class.java
            ) { _,_,_ ->
                Result.Allow(5000)
            }.build()

        println(stateChart.toUmlDiagram())
        stateChart.run()

        stateChart.sendEvent(Forbidden(false))
        assertEquals(stateChart.state(), greenLight)

        stateChart.sendEvent(Forbidden(true))
        assertEquals(stateChart.state(), yellowLight)
    }
}