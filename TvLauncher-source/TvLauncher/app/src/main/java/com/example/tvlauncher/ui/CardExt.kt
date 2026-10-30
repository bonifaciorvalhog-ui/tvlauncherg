package com.example.tvlauncher.ui

import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.tvlauncher.model.HomeItem

internal fun ImageView.loadItem(item: HomeItem) {
    val local = item.drawable
    if (local != null) {
        Glide.with(this).clear(this)
        setImageDrawable(local)
    } else {
        Glide.with(this).load(item.imageUri).centerCrop().into(this)
    }
}

/** Imagem do destaque, com transição suave entre um item e outro. */
internal fun ImageView.loadHero(uri: Uri?) {
    if (uri == null) {
        Glide.with(this).clear(this)
        setImageDrawable(null)
    } else {
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(500))
            .into(this)
    }
}

internal fun View.animateFocus(focused: Boolean, scale: Float) {
    animate()
        .scaleX(if (focused) scale else 1f)
        .scaleY(if (focused) scale else 1f)
        .translationZ(if (focused) 12f else 0f)
        .setDuration(150)
        .start()
}

internal fun View.resetFocusAnimation() {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    translationZ = 0f
}
