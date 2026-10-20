package com.example.rowin_adlproject

import com.example.rowin_adlproject.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dynamic greeting (feature #2) must follow the device clock.
 */
class HomeViewModelGreetingTest {

    @Test
    fun `morning hours greet good morning`() {
        assertEquals("Good morning", HomeViewModel.greetingFor(5))
        assertEquals("Good morning", HomeViewModel.greetingFor(11))
    }

    @Test
    fun `afternoon hours greet good afternoon`() {
        assertEquals("Good afternoon", HomeViewModel.greetingFor(12))
        assertEquals("Good afternoon", HomeViewModel.greetingFor(16))
    }

    @Test
    fun `evening hours greet good evening`() {
        assertEquals("Good evening", HomeViewModel.greetingFor(17))
        assertEquals("Good evening", HomeViewModel.greetingFor(21))
    }

    @Test
    fun `late hours still greet good evening instead of good night`() {
        // "Good night" is never allowed; night hours fall through to evening.
        assertEquals("Good evening", HomeViewModel.greetingFor(22))
        assertEquals("Good evening", HomeViewModel.greetingFor(3))
        assertEquals("Good evening", HomeViewModel.greetingFor(4))
    }
}
