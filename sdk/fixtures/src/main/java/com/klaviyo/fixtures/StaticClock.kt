package com.klaviyo.fixtures

import com.klaviyo.core.config.Clock
import kotlin.time.Duration

/**
 * Implementation of Clock for unit tests
 *
 * @property time
 */
class StaticClock(var time: Long, private val formatted: String) : Clock {
    override fun currentTimeMillis(): Long = time
    override fun isoTime(milliseconds: Long): String = formatted

    override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable {
        val scheduledTask = ScheduledTask(currentTimeMillis() + delay, task)
        scheduledTasks.add(scheduledTask)
        return object : Clock.Cancellable {
            override fun runNow() = task().also { cancel() }
            override fun cancel(): Boolean = scheduledTasks.remove(scheduledTask)
        }
    }

    class ScheduledTask(val time: Long, val task: () -> Unit)

    val scheduledTasks = mutableListOf<ScheduledTask>()

    fun execute(advance: Duration) = execute(advance.inWholeMilliseconds)

    fun execute(advance: Long = 0) {
        time += advance

        scheduledTasks.filter { scheduledTask ->
            scheduledTask.time <= currentTimeMillis()
        }.forEach { scheduledTask ->
            scheduledTask.task()
            scheduledTasks.remove(scheduledTask)
        }
    }
}
