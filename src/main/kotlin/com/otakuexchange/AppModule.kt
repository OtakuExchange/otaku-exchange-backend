package com.otakuexchange.di

import org.koin.dsl.module
import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.domain.repositories.IBookmarkRepository
import com.otakuexchange.domain.repositories.IEntityRepository
import com.otakuexchange.domain.repositories.IOrderRecordRepository
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.domain.services.OrderMatchingService
import com.otakuexchange.domain.repositories.ISubtopicRepository
import com.otakuexchange.infra.repositories.NeonSubtopicRepository
import com.otakuexchange.infra.repositories.NeonTopicRepository
import com.otakuexchange.infra.repositories.NeonBookmarkRepository
import com.otakuexchange.infra.repositories.NeonEntityRepository
import com.otakuexchange.infra.repositories.NeonEventRepository
import com.otakuexchange.infra.repositories.NeonMarketRepository
import com.otakuexchange.infra.repositories.NeonUserRepository
import com.otakuexchange.infra.repositories.NeonCommentRepository
import com.otakuexchange.infra.repositories.NeonOrderRecordRepository
import com.otakuexchange.infra.repositories.NeonTradeHistoryRepository
import com.otakuexchange.infra.repositories.RedisOrderBookRepository
import com.otakuexchange.application.controllers.IRouteController
import com.otakuexchange.application.controllers.TopicController
import com.otakuexchange.application.controllers.MarketController
import com.otakuexchange.application.controllers.EventController
import com.otakuexchange.application.controllers.AuthController
import com.otakuexchange.application.controllers.OrderController
import com.otakuexchange.application.controllers.SubtopicController
import org.koin.core.qualifier.named

val appModule = module {
    // Repositories
    single<ITopicRepository> { NeonTopicRepository() }
    single<IEventRepository> { NeonEventRepository() }
    single<IMarketRepository> { NeonMarketRepository() }
    single<IUserRepository> { NeonUserRepository() }
    single<ICommentRepository> { NeonCommentRepository() }
    single<IBookmarkRepository> { NeonBookmarkRepository() }
    single<IEntityRepository> { NeonEntityRepository() }
    single<IOrderRecordRepository> { NeonOrderRecordRepository() }
    single<ITradeHistoryRepository> { NeonTradeHistoryRepository() }
    single<ISubtopicRepository> { NeonSubtopicRepository() }
    single<IOrderBookRepository> { RedisOrderBookRepository() }

    // Services
    single { OrderMatchingService(get(), get(), get()) }

    // Controllers
    single<IRouteController>(named("topicController")) { TopicController(get()) }
    single<IRouteController>(named("marketController")) { MarketController(get(), get()) }
    single<IRouteController>(named("eventController")) { EventController(get(), get(), get(), get()) }
    single<IRouteController>(named("subtopicController")) { SubtopicController(get(), get()) }
    single<IRouteController>(named("authController")) { AuthController(get()) }
    single<IRouteController>(named("orderController")) { OrderController(get(), get(), get(), get(), get(), get(), get()) }
}