package com.boardgames.schemas

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class ExposedUser(
    val username: String,
    val password: String?,
    val email: String?,
    val creationDate: String? = LocalDateTime.now().toString()
)

class UserService(database: Database) {
    object Users : IntIdTable() {
        val username = varchar("username", length = 50)
        val email = varchar("email", length = 255)
        val password = varchar("password", length = 255)
        val creationDate = datetime("date_created").clientDefault{ LocalDateTime.now()}
    }

    init { transaction(database) { SchemaUtils.create(Users) } }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(account: ExposedUser): Int = dbQuery {
        val password = account.password!!.toCharArray()
        // Insert the user details into the Users table and retrieve the generated ID
        Users.insert {
            it[this.password] = BCrypt.withDefaults().hashToString(12, password)
            it[this.username] = account.username
            account.email?.let { email -> it[this.email] = email }
        }[Users.id].value
    }

    suspend fun read(id: Int, isOwner: Boolean): ExposedUser? {
        return dbQuery {
            // Select user details from the Users table based on the provided ID
            Users.select { Users.id eq id }.mapNotNull { row ->
                ExposedUser(
                    username = row[Users.username], password = null,
                    email = if (isOwner) row[Users.email] else null,
                    creationDate = row[Users.creationDate].toString()
                )
            }.singleOrNull() // Return a single result or null if not found
        }
    }

    suspend fun update(id: Int, username: String?, password: String?, email: String?) {
        dbQuery {
            Users.update({ Users.id eq id }) {account ->
                // Update the user details based on the provided parameters.
                email?.let { account[this.email] = it }
                username?.let { account[this.username] = it }
                password?.let {
                    // Hash the new password using BCrypt and update it in the database
                    val hashedPassword = BCrypt.withDefaults().hashToString(12, it.toCharArray())
                    account[this.password] = hashedPassword
                }
            }
        }
    }

    suspend fun authenticate(credentials: ExposedUser): Int? {
        return dbQuery {
            // Retrieve the user account based on the provided username
            val query = Users.select { Users.username eq credentials.username }
            val account = query.firstOrNull() ?: return@dbQuery null
            // Verify the password using BCrypt
            val password = credentials.password!!.toCharArray()
            val verified = BCrypt.verifyer().verify(password, account[Users.password])
            // Return the user ID if the password is verified, otherwise null.
            return@dbQuery if (verified.verified) account[Users.id].value else null
        }
    }

    suspend fun delete(id: Int) { dbQuery { Users.deleteWhere { Users.id.eq(id) } } }
}
