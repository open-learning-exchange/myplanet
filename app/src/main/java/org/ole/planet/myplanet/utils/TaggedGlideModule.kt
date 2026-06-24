package org.ole.planet.myplanet.utils

import android.content.Context
import android.net.TrafficStats
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelCache
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

private const val GLIDE_TRAFFIC_TAG = 0x47_4C_44
private const val MODEL_CACHE_SIZE = 500L

@GlideModule
class TaggedGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, TaggedHttpLoaderFactory())
    }

    override fun isManifestParsingEnabled(): Boolean = false

    private class TaggedHttpLoaderFactory : ModelLoaderFactory<GlideUrl, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> {
            val modelCache = ModelCache<GlideUrl, GlideUrl>(MODEL_CACHE_SIZE)
            return TaggedHttpLoader(HttpGlideUrlLoader(modelCache))
        }

        override fun teardown() {}
    }

    private class TaggedHttpLoader(private val delegate: ModelLoader<GlideUrl, InputStream>) : ModelLoader<GlideUrl, InputStream> {
        override fun buildLoadData(model: GlideUrl, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
            val loadData = delegate.buildLoadData(model, width, height, options) ?: return null
            return ModelLoader.LoadData(loadData.sourceKey, TaggedDataFetcher(loadData.fetcher))
        }

        override fun handles(model: GlideUrl): Boolean = delegate.handles(model)
    }

    private class TaggedDataFetcher(private val delegate: DataFetcher<InputStream>) : DataFetcher<InputStream> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            TrafficStats.setThreadStatsTag(GLIDE_TRAFFIC_TAG)
            try {
                delegate.loadData(priority, callback)
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }

        override fun cleanup() = delegate.cleanup()

        override fun cancel() = delegate.cancel()

        override fun getDataClass(): Class<InputStream> = delegate.dataClass

        override fun getDataSource(): DataSource = delegate.dataSource
    }
}
