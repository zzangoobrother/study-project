package com.example.libraryapp.service.user

import com.example.libraryapp.domain.user.User
import com.example.libraryapp.domain.user.UserRepository
import com.example.libraryapp.dto.user.request.UserCreateRequest
import com.example.libraryapp.dto.user.request.UserUpdateRequest
import com.example.libraryapp.dto.user.response.UserResponse
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun saveUser(request: UserCreateRequest) {
        val newUser = User(request.name, request.age)
        userRepository.save(newUser)
    }

    @Transactional
    fun getUsers(): List<UserResponse> {
        return userRepository.findAll()
            .map { user -> UserResponse(user) }
    }

    @Transactional
    fun updateUserName(request: UserUpdateRequest) {
        val user = userRepository.findById(request.id).orElseThrow(::IllegalArgumentException)
        user.updateName(request.name)
    }

    @Transactional
    fun deleteUser(name: String) {
        val user = userRepository.findByName(name).orElseThrow(::IllegalArgumentException)
        userRepository.delete(user)
    }
}