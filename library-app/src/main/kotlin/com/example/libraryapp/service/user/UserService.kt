package com.example.libraryapp.service.user

import com.example.libraryapp.domain.user.User
import com.example.libraryapp.domain.user.UserRepository
import com.example.libraryapp.dto.user.request.UserCreateRequest
import com.example.libraryapp.dto.user.request.UserUpdateRequest
import com.example.libraryapp.dto.user.response.UserResponse
import com.example.libraryapp.util.fail
import com.example.libraryapp.util.findByIdOrThrow
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
            .map { user -> UserResponse.of(user) }
    }

    @Transactional
    fun updateUserName(request: UserUpdateRequest) {
        val user = userRepository.findByIdOrThrow(request.id)
        user.updateName(request.name)
    }

    @Transactional
    fun deleteUser(name: String) {
        val user = userRepository.findByName(name) ?: fail()
        userRepository.delete(user)
    }
}