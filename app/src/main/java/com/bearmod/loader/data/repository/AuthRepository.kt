package com.bearmod.loader.data.repository

/**
 * Backwards-compatible alias so callers can migrate from KeyAuthRepository to AuthRepository
 * without changing the existing implementation immediately.
 *
 * This file intentionally contains only a typealias to the existing KeyAuthRepository.
 * Later we can replace the implementation and migrate consumers in small steps.
 */
typealias AuthRepository = KeyAuthRepository
