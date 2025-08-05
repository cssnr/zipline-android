package org.cssnr.zipline.db

import org.cssnr.zipline.api.ServerApi.User

class UserRepository(private val dao: UserDao) {
    suspend fun updateUser(url: String, user: User): UserEntity {
        val userEntity = UserEntity(
            url = url,
            id = user.id,
            username = user.username,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            role = user.role,
            totpSecret = user.totpSecret,
        )
        dao.insertUser(userEntity)
        return userEntity
    }
}
