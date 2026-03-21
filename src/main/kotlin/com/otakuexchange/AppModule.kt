package com.otakuexchange.di

import org.koin.dsl.module
import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.domain.repositories.IBookmarkRepository
import com.otakuexchange.domain.repositories.IEntityRepository
import com.otakuexchange.domain.repositories.ISubtopicRepository
import com.otakuexchange.infra.repositories.NeonSubtopicRepository
import com.otakuexchange.infra.repositories.NeonTopicRepository
import com.otakuexchange.infra.repositories.NeonBookmarkRepository
import com.otakuexchange.infra.repositories.NeonEntityRepository
import com.otakuexchange.infra.repositories.NeonEventRepository
import com.otakuexchange.infra.repositories.NeonMarketRepository
import com.otakuexchange.infra.repositories.NeonUserRepository
import com.otakuexchange.infra.repositories.NeonCommentRepository
import com.otakuexchange.application.controllers.IRouteController
import com.otakuexchange.application.controllers.TopicController
import com.otakuexchange.application.controllers.MarketController
import com.otakuexchange.application.controllers.EventController
import com.otakuexchange.application.controllers.AuthController
import com.otakuexchange.application.controllers.SubtopicController
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.infra.repositories.RedisOrderBookRepository
import org.koin.core.qualifier.named


/**
 * Koin application-wide module.  Bind all concrete implementations of
 * domain ports (repositories, services, etc.) here so they can be injected
 * throughout the codebase.
 */
val appModule = module {
    single<ITopicRepository> { NeonTopicRepository() }
    single<IEventRepository> { NeonEventRepository() }
    single<IMarketRepository> { NeonMarketRepository() }
    single<IUserRepository> { NeonUserRepository() }
    single<ICommentRepository> { NeonCommentRepository() }
    single<IBookmarkRepository> { NeonBookmarkRepository() }
    single<IEntityRepository> { NeonEntityRepository() }
    single<ISubtopicRepository> { NeonSubtopicRepository() }
    single<IOrderBookRepository> { RedisOrderBookRepository() }

    single<IRouteController>(named("topicController")) { TopicController(get()) }
    single<IRouteController>(named("marketController")) { MarketController(get(), get()) }
    single<IRouteController>(named("eventController")) { EventController(get(), get(), get(), get()) }
    single<IRouteController>(named("subtopicController")) { SubtopicController(get(), get()) }
    single<IRouteController>(named("authController")) { AuthController(get()) }
}