package com.klaviyo.core.model

/**
 * Base class used to provide polymorphic properties to the use of profile and event keys
 */
abstract class Keyword(val name: String) {
    override fun toString(): String = name
    override fun equals(other: Any?): Boolean = (other as? Keyword).toString() == toString()
    override fun hashCode(): Int = name.hashCode()
}
