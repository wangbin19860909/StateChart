package com.benny.library.statechart

import com.benny.library.statechart.Result
import org.junit.Test

import org.junit.Assert.*

import com.benny.library.statechart.State

class SingleLayerStateChartTest {
    class GreenCountDown : Event()
    class RedCountDown: Event()
    class YellowCountDown: Event()

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
            .state(redLight)
            .transition(
                "yellow_to_red",
                setOf(yellowLight),
                redLight,
                YellowCountDown::class.java
            ) { _, _ ->
                Result.Allow(10000)
            }.transition(
                "red_to_yellow",
                setOf(redLight),
                yellowLight,
                RedCountDown::class.java
            ) { _,_ ->
                Result.Allow(3000)
            }.transition(
                "yellow_to_green",
                setOf(greenLight),
                yellowLight,
                GreenCountDown::class.java
            ) { _,_ ->
                Result.Allow(5000)
            }.build()

        println(stateChart.toUmlDiagram())

        stateChart.run()
    }
}