"""按目标比例从中心裁切铺满（design 决策 1）：MoneyPrinterTurbo 按「适应」缩放会留黑边，生成前先裁好"""
from PIL import Image, ImageOps

SIZES = {"9:16": (1080, 1920), "16:9": (1920, 1080)}


def cover_crop(src: str, dst: str, aspect: str) -> tuple[int, int]:
    """把 src 裁切缩放成 aspect 对应的尺寸存到 dst（JPEG），返回输出尺寸。先按 EXIF 方向转正"""
    size = SIZES[aspect]
    with Image.open(src) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        out = ImageOps.fit(im, size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
        out.save(dst, "JPEG", quality=92)
    return out.size
