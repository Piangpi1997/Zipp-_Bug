package com.webtoapp.core.i18n

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MyanmarLanguageTest {
    @Test
    fun `Myanmar choice persists and other languages clear the choice`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pp_language", Context.MODE_PRIVATE).edit().clear().commit()
        Strings.initialize(context)
        Strings.setLanguage(AppLanguage.ENGLISH)
        val manager = LanguageManager.getInstance(context)

        manager.setMyanmarLanguage()
        assertThat(Strings.myanmarEnabled.value).isTrue()
        assertThat(Strings.myApps).isEqualTo("ကျွန်ုပ်၏ အက်ပ်များ")
        assertThat(Strings.typewriterText2).isEqualTo(StringsA.typewriterText2)
        assertThat(manager.getCurrentLanguage()).isEqualTo(AppLanguage.ENGLISH)

        Strings.initialize(context)
        assertThat(Strings.myanmarEnabled.value).isTrue()
        manager.setLanguage(AppLanguage.JAPANESE)
        assertThat(Strings.myanmarEnabled.value).isFalse()
        Strings.initialize(context)
        assertThat(Strings.myanmarEnabled.value).isFalse()
    }
}
