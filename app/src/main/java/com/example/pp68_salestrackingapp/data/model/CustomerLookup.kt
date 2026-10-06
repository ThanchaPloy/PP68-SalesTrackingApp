package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

/** Minimal projection for ERP customers. These rows are never persisted in Room. */
data class CustomerLookup(
    @SerializedName("customer_code") val customerCode: String,
    @SerializedName("customer_name") val customerName: String
)

data class CustomerLookupPage(
    val items: List<CustomerLookup>,
    @SerializedName("next_cursor") val nextCursor: String? = null
)
