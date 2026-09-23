package com.duress.guardian.lock

/** Outcome of checking an entered PIN against the stored real / duress hashes. */
enum class PinResult { REAL, DURESS, INVALID }
