package com.otakuexchange.application.controllers

import io.ktor.server.routing.Route

interface IRouteController {
    fun registerRoutes(route: Route)
}