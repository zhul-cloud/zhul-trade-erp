"""spec「视频生成」：方形、竖拍、横拍三种源图都裁成目标尺寸，铺满没有黑边"""
import os
import tempfile
import unittest

from PIL import Image

from crop import cover_crop


class CoverCropTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def source(self, w, h):
        path = os.path.join(self.dir, f"{w}x{h}.png")
        Image.new("RGB", (w, h), (200, 30, 30)).save(path)
        return path

    def test_three_sources_fill_both_aspects(self):
        for w, h in [(3024, 3024), (960, 1280), (1280, 960)]:
            src = self.source(w, h)
            for aspect, size in [("9:16", (1080, 1920)), ("16:9", (1920, 1080))]:
                dst = os.path.join(self.dir, f"{w}x{h}-{aspect.replace(':', 'x')}.jpg")
                self.assertEqual(cover_crop(src, dst, aspect), size)
                with Image.open(dst) as out:
                    self.assertEqual(out.size, size)
                    # 四个角都是原图颜色（没有黑边）
                    for xy in [(2, 2), (size[0] - 3, 2), (2, size[1] - 3), (size[0] - 3, size[1] - 3)]:
                        r, g, b = out.getpixel(xy)
                        self.assertGreater(r, 150, f"{w}x{h} → {aspect} 角上有黑边")

    def test_exif_rotation_applied(self):
        src = os.path.join(self.dir, "rotated.jpg")
        img = Image.new("RGB", (1280, 960), (200, 30, 30))
        exif = img.getexif()
        exif[0x0112] = 6  # 拍摄时手机竖着：显示时要顺时针转 90 度
        img.save(src, exif=exif)
        dst = os.path.join(self.dir, "rotated-out.jpg")
        self.assertEqual(cover_crop(src, dst, "9:16"), (1080, 1920))


if __name__ == "__main__":
    unittest.main()
