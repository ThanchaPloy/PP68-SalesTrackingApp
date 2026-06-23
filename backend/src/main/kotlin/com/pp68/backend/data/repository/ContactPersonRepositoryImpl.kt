package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ContactPersonTable
import com.pp68.backend.domain.entity.ContactPerson
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ContactPersonRepositoryImpl {

    private fun ResultRow.toContact() = ContactPerson(
        contactId    = this[ContactPersonTable.contactId],
        customerCode = this[ContactPersonTable.customerCode],
        contactName  = this[ContactPersonTable.contactName],
        phone        = this[ContactPersonTable.phone],
        mobilePhone  = this[ContactPersonTable.mobilePhone],
        email        = this[ContactPersonTable.email],
        fax          = this[ContactPersonTable.fax],
        telexNo      = this[ContactPersonTable.telexNo],
        isPrimary    = this[ContactPersonTable.isPrimary],
        createdAt    = this[ContactPersonTable.createdAt]?.toString(),
        updatedAt    = this[ContactPersonTable.updatedAt]?.toString()
    )

    suspend fun findByCustomerCode(customerCode: String): List<ContactPerson> = dbQuery {
        ContactPersonTable.select { ContactPersonTable.customerCode eq customerCode }.map { it.toContact() }
    }

    suspend fun findByCustomerCodes(customerCodes: List<String>): List<ContactPerson> = dbQuery {
        ContactPersonTable.select { ContactPersonTable.customerCode inList customerCodes }.map { it.toContact() }
    }

    suspend fun findById(contactId: Long): ContactPerson? = dbQuery {
        ContactPersonTable.select { ContactPersonTable.contactId eq contactId }.singleOrNull()?.toContact()
    }

    suspend fun create(contact: ContactPerson): ContactPerson = dbQuery {
        val id = ContactPersonTable.insert {
            it[customerCode] = contact.customerCode
            it[contactName]  = contact.contactName
            it[phone]        = contact.phone
            it[mobilePhone]  = contact.mobilePhone
            it[email]        = contact.email
            it[fax]          = contact.fax
            it[telexNo]      = contact.telexNo
            it[isPrimary]    = contact.isPrimary
        } get ContactPersonTable.contactId
        ContactPersonTable.select { ContactPersonTable.contactId eq id }.single().toContact()
    }

    suspend fun update(contactId: Long, updates: Map<String, Any?>): ContactPerson? = dbQuery {
        ContactPersonTable.update({ ContactPersonTable.contactId eq contactId }) { stmt ->
            updates["contact_name"]?.let { v -> stmt[contactName] = v as String }
            updates["phone"]?.let        { v -> stmt[phone]       = v as String }
            updates["mobile_phone"]?.let { v -> stmt[mobilePhone] = v as String }
            updates["email"]?.let        { v -> stmt[email]       = v as String }
            updates["fax"]?.let          { v -> stmt[fax]         = v as String }
            updates["telex_no"]?.let     { v -> stmt[telexNo]     = v as String }
            updates["is_primary"]?.let   { v -> stmt[isPrimary]   = v as Boolean }
        }
        ContactPersonTable.select { ContactPersonTable.contactId eq contactId }.singleOrNull()?.toContact()
    }

    suspend fun delete(contactId: Long): Boolean = dbQuery {
        ContactPersonTable.deleteWhere { ContactPersonTable.contactId eq contactId } > 0
    }

    suspend fun deleteByCustomerCode(customerCode: String): Boolean = dbQuery {
        ContactPersonTable.deleteWhere { ContactPersonTable.customerCode eq customerCode } > 0
    }
}
