package com.boardgames.schemas

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Contextual
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class ExposedUser(
    val username: String,
    val password: String,
    val email: String,
    @Contextual
    val creationDate: LocalDateTime?
)

class UserService(database: Database) {
    object Users : IntIdTable() {
        val username = varchar("username", length = 50)
        val email = varchar("email", length = 255)
        val password = varchar("password", length = 50)
        val creationDate = datetime("date_created").clientDefault{ LocalDateTime.now()}
    }

    init { transaction(database) { SchemaUtils.create(Users) } }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(account: ExposedUser): Int = dbQuery {
        Users.insert {
            it[username] = account.username
            it[password] = account.password
        }[Users.id].value
    }

    suspend fun read(id: Int): ExposedUser? {
        return dbQuery {
            Users.select { Users.id eq id }.mapNotNull { row ->
                ExposedUser(
                    username = row[Users.username],
                    password = row[Users.password],
                    email = row[Users.email],
                    creationDate = row[Users.creationDate]
                )
            }.singleOrNull()
        }
    }

    suspend fun update(id: Int, fields: Map<String, Any>) {
        dbQuery {
            Users.update({ Users.id eq id }) {
                fields.forEach { (columnName, value) ->
                    val column = Users.columns.single { it.name.equals(columnName, ignoreCase = true) }
                    it[column as Column<Any>] = value
                }
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery { Users.deleteWhere { Users.id.eq(id) } }
    }
}
