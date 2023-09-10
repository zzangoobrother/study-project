package com.example.libraryapp.domain.user

interface UserRepositoryCustom {
    fun findAllWithHistories(): List<User>
}