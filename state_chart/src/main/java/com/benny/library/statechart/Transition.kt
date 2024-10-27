package com.benny.library.statechart

sealed class Result<T> {
  class Allow<Param>(
    val param: Param,
    val reason: Reason = Reason(),
  ): Result<Param>() { }

  class Disallow<T> : Result<T>()
}

/**
 * state transition
 * @param condition  Result.Allow means transition can execute, else not
 */
internal class Transition<T: Any, EV: Event, Context: Any> (
  val name: String,
  val from: Set<State<*, Context>>,
  val to: State<T, Context>,
  val eventClass: Class<EV>,
  private val condition: (EV, State<*, Context>, Context) -> Result<T>
) {
  fun transit(event: Event, state: State<*, *>, context: Any): Result<T> {
    if (event.javaClass != eventClass) {
      return Result.Disallow()
    }

    @Suppress("UNCHECKED_CAST")
    return condition(event as EV, state as State<*, Context>, context as Context)
  }

  override fun toString(): String {
    return name
  }
}

