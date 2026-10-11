package com.example.poolassistant

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poolassistant.overlay.TableCornerSelectionView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableCornerSelectionTest {
    private fun tap(view: TableCornerSelectionView, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun fourInsideCornersComplete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selected: RectF? = null
        var canceled = false
        val view = TableCornerSelectionView(context, { selected = it }, { canceled = true })
        view.layout(0, 0, 1612, 720)
        tap(view, 320f, 170f)
        tap(view, 1295f, 170f)
        tap(view, 1295f, 655f)
        assertNull(selected)
        tap(view, 320f, 655f)
        val bounds = selected
        assertNotNull(bounds)
        assertEquals(320f, bounds!!.left, .01f)
        assertEquals(170f, bounds.top, .01f)
        assertEquals(1295f, bounds.right, .01f)
        assertEquals(655f, bounds.bottom, .01f)
        assertTrue(!canceled)
    }

    @Test fun cancelDoesNotSave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selected: RectF? = null
        var canceled = false
        val view = TableCornerSelectionView(context, { selected = it }, { canceled = true })
        view.layout(0, 0, 1612, 720)
        tap(view, 320f, 170f)
        tap(view, 20f, 690f)
        assertTrue(canceled)
        assertNull(selected)
    }
}
