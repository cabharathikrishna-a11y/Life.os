package com.example.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseRepository {
    private val _usersState = MutableStateFlow<Map<String, UserRemote>>(emptyMap())
    val usersState: StateFlow<Map<String, UserRemote>> = _usersState.asStateFlow()

    private val lock = Any()

    fun updateUsers(newUsers: Map<String, UserRemote>) {
        synchronized(lock) {
            // Create a completely deep copy/fresh map copy of UserRemote to prevent reference leaks and concurrent modifications in downstream collections/UI
            val copy = newUsers.mapValues { (_, user) ->
                user.copy(
                    todaysFocusRecords = user.todaysFocusRecords?.toList() ?: emptyList()
                )
            }
            _usersState.value = copy
        }
    }

    fun getUsers(): Map<String, UserRemote> {
        synchronized(lock) {
            return _usersState.value
        }
    }
}
