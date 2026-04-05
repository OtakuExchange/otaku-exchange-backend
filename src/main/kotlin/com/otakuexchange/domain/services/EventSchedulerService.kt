package com.otakuexchange.domain.services

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.repositories.IEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Schedules automatic staking closure for events at their exact closeTime.
 *
 * - On startup, call [scheduleAll] to reschedule all open events loaded from DB.
 * - When an event is created or its closeTime is updated, call [schedule].
 * - If closeTime changes, the existing job is cancelled and rescheduled.
 * - Manually closed/reopened events are unaffected — [cancel] removes the job.
 */
class EventSchedulerService(
    private val eventRepository: IEventRepository
) {
    private val logger = LoggerFactory.getLogger(EventSchedulerService::class.java)
    private val scope  = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val jobs   = ConcurrentHashMap<Uuid, Job>()

    /** Schedule a single event to auto-close at its closeTime. */
    fun schedule(event: Event) {
        val delay = event.closeTime - Clock.System.now()
        if (delay.isNegative() || delay.inWholeMilliseconds == 0L) {
            logger.info("Event ${event.id} (${event.name}) closeTime already passed — skipping schedule")
            return
        }

        // Cancel any existing job for this event (e.g. closeTime was updated)
        jobs[event.id]?.cancel()

        val job = scope.launch {
            logger.info("Scheduled auto-close for event ${event.id} (${event.name}) in ${delay.inWholeMinutes}m")
            delay(delay)
            logger.info("Auto-closing staking for event ${event.id} (${event.name})")
            eventRepository.closeStaking(event.id)
            jobs.remove(event.id)
        }

        jobs[event.id] = job
    }

    /** Cancel the scheduled auto-close for an event (e.g. admin manually manages it). */
    fun cancel(eventId: Uuid) {
        jobs.remove(eventId)?.cancel()
        logger.info("Cancelled scheduled auto-close for event $eventId")
    }

    /** Reschedule all currently open events on startup. */
    suspend fun scheduleAll() {
        val openEvents = eventRepository.getOpenEventsPastCloseTime()
            .also { logger.info("Rescheduling auto-close for ${it.size} open events on startup") }
        // Also get events not yet past close time
        // getOpenEventsPastCloseTime only returns already-expired ones for immediate close
        // We need all open events — use getEventsByStatus
        val allToScheduleEvents = eventRepository.getEventsByStatus("open", null) + eventRepository.getEventsByStatus("hidden", null)
        allToScheduleEvents.forEach { eventWithBookmark ->
            val event = Event(
                id             = eventWithBookmark.id,
                topicId        = eventWithBookmark.topicId,
                format         = eventWithBookmark.format,
                name           = eventWithBookmark.name,
                description    = eventWithBookmark.description,
                closeTime      = eventWithBookmark.closeTime,
                status         = eventWithBookmark.status,
                resolutionRule = eventWithBookmark.resolutionRule,
                logoPath       = eventWithBookmark.logoPath,
                pandaScoreId   = eventWithBookmark.pandaScoreId
            )
            schedule(event)
        }

        // Immediately close any that are already past close time
        openEvents.forEach { event ->
            logger.info("Immediately closing staking for past-due event ${event.id} (${event.name})")
            eventRepository.closeStaking(event.id)
        }
    }
}