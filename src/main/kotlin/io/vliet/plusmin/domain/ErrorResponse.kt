package io.vliet.plusmin.domain

    data class ErrorResponse(
        val errorCode: String,
        val errorMessage: String
    )