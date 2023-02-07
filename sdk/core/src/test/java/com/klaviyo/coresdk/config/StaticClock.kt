package com.klaviyo.coresdk.config

/**
 * Implementation of Clock for unit tests
 *
 * @property time
 */
internal class StaticClock(var time: Long, private val formatted: String) : Clock {
    override fun currentTimeMillis(): Long = time
    override fun currentTimeAsString(): String = formatted

    override fun schedule(delay: Long, task: () -> Unit): Clock.Cancellable {
        val scheduledTask = ScheduledTask(currentTimeMillis() + delay, task)
        scheduledTasks.add(scheduledTask)
        return object : Clock.Cancellable {
            override fun cancel(): Boolean {
                scheduledTasks.remove(scheduledTask)
                return true
            }
        }
    }

    class ScheduledTask(val time: Long, val task: () -> Unit)

    val scheduledTasks = mutableListOf<ScheduledTask>()

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
