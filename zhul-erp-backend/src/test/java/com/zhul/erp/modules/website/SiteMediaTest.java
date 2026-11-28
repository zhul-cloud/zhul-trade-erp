package com.zhul.erp.modules.website;

import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.framework.storage.StorageProperties;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.service.ListingTaskService;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.media.support.MediaCategory;
import com.zhul.erp.modules.website.service.SiteMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** tasks 6.3a：私有素材复制到公开桶（重复发布不重复复制），官方图片用原地址不复制；预览只算链接 */
class SiteMediaTest {

    @TempDir
    Path root;

    private FileStorageService files;
    private ListingTaskService listing;
    private MediaLibraryService library;
    private SiteMediaService service;
    private final ListingTaskDO task = new ListingTaskDO();

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties props = new StorageProperties();
        LocalFileStorage local = new LocalFileStorage(root.resolve("priv").toString(), root.resolve("pub").toString(), "s");
        files = new FileStorageService(new FileStorages(props, local, null), props);
        Path priv = root.resolve("priv/media/1005/a.jpg");
        Files.createDirectories(priv.getParent());
        Files.writeString(priv, "jpg", StandardCharsets.UTF_8);
        listing = mock(ListingTaskService.class);
        library = mock(MediaLibraryService.class);
        service = new SiteMediaService(listing, library, files);

        MediaItemVO own = new MediaItemVO();
        own.setItemId(501L);
        own.setKind(1);
        own.setCategory(MediaCategory.MARKETING.code());
        MediaItemVO official = new MediaItemVO();
        official.setOfficialTable("product_media");
        official.setOfficialId(9L);
        official.setKind(1);
        official.setPublicUrl("https://bucket.example.com/product/a.jpg");
        MediaItemVO doc = new MediaItemVO();
        doc.setOfficialTable("product_document");
        doc.setKind(3);
        doc.setPublicUrl("https://bucket.example.com/a.pdf");
        when(listing.siteMedia(any())).thenReturn(new ListingTaskService.SiteMedia(List.of(official, own, doc), false));
        when(library.listingSource(501L)).thenReturn(new MediaLibraryService.FileSource("local", "media/1005/a.jpg", "a.jpg", null, false));
    }

    @Test
    void publish_copiesPrivateOnce_officialKeepsUrl() {
        SiteMediaService.Result first = service.of(7L, "siemens-x", task, "Siemens X", true);
        assertThat(first.photos()).extracting(m -> m.get("src"))
                .containsExactly("https://bucket.example.com/product/a.jpg", files.publicUrl("local", "website/7/siemens-x/501.jpg"));
        assertThat(first.photos()).extracting(m -> m.get("tag")).containsExactly("Photo", "Marketing");
        assertThat(first.videos()).isEmpty();
        assertThat(first.copied()).isEqualTo(1);
        assertThat(Files.exists(root.resolve("pub/website/7/siemens-x/501.jpg"))).isTrue();

        SiteMediaService.Result again = service.of(7L, "siemens-x", task, "Siemens X", true);
        assertThat(again.copied()).isZero();
        assertThat(again.photos()).isEqualTo(first.photos());
    }

    @Test
    void preview_computesUrlWithoutCopying() {
        SiteMediaService.Result r = service.of(7L, "siemens-x", task, "Siemens X", false);
        assertThat(r.photos()).hasSize(2);
        assertThat(r.copied()).isZero();
        assertThat(Files.exists(root.resolve("pub/website/7/siemens-x/501.jpg"))).isFalse();
    }

    @Test
    void referenceMedia_usesSendableSource_notListingSource() {
        when(listing.siteMedia(any())).thenReturn(new ListingTaskService.SiteMedia(List.of(video()), true));
        when(library.source(any())).thenReturn(new MediaLibraryService.FileSource("local", "media/1005/a.jpg", "a.mp4", null, false));
        SiteMediaService.Result r = service.of(7L, "siemens-x", task, "Siemens X", false);
        assertThat(r.reference()).isTrue();
        assertThat(r.videos()).containsExactly(Map.of("src", files.publicUrl("local", "website/7/siemens-x/502.jpg"), "title", "Siemens X"));
        verify(library, never()).listingSource(any());
    }

    private static MediaItemVO video() {
        MediaItemVO v = new MediaItemVO();
        v.setItemId(502L);
        v.setKind(2);
        return v;
    }
}
