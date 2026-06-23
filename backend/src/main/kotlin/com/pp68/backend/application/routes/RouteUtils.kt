package com.pp68.backend.application.routes

// PostgREST sends single-value filters as "eq.value" — strip the prefix so our DB queries work
fun String?.stripEq(): String? = this?.removePrefix("eq.")