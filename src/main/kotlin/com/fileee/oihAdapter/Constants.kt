package com.fileee.oihAdapter

const val API_BASE_URL = "https://api.fileee.com/v2"

const val PERSONS_API_URL = "$API_BASE_URL/persons"
const val USER_INFO = "$API_BASE_URL/authorization/user-info"
const val REFRESH_TOKEN_URL = "$API_BASE_URL/authorization/token"

// json configuration constants
const val OAUTH_KEY = "oauth"
const val ACCESS_TOKEN = "access_token"
const val REFRESH_TOKEN = "refresh_token"
const val MODIFIED_AFTER_KEY = "modified_after"

const val CONTACT_TYPE = "contacts"
const val DELETED_CONTACT_TYPE = "deleted_contacts"

const val DELETED_KEY = "deleted"