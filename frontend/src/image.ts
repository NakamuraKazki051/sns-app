// 投稿に付ける画像を、送る前にブラウザで整える

/** サーバーが受け付ける上限(要件 F-14) */
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
/** 縮める前の元の画像はここまで選べる(スマホの写真は5MBを超えることがあるので、縮めて5MB以内にする) */
export const SOURCE_MAX_BYTES = 20 * 1024 * 1024;
/** 長い辺をここまで縮める。タイムラインで見るには十分で、送る量と保存する量が減る */
export const MAX_SIDE = 2048;

const ACCEPTED = ["image/jpeg", "image/png"];

export class ImageError extends Error {}

/**
 * 画像を縮めて、描き直したもの(JPEG か PNG)を返す。描き直すことで、写真に入っている撮影場所(GPS)などの
 * 情報(EXIF)が消える。スマホの写真の向き(縦横)は、描くときにブラウザが合わせてくれる
 */
export async function prepareImage(file: File): Promise<Blob> {
  if (!ACCEPTED.includes(file.type)) {
    throw new ImageError("画像は JPEG か PNG を選んでください");
  }
  if (file.size > SOURCE_MAX_BYTES) {
    throw new ImageError("画像が大きすぎます(20MBまで)");
  }
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new ImageError("画像を読み込めませんでした。別の画像でお試しください");
  }
  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));

    // PNG(スクリーンショットや透過のある画像)は PNG のまま。大きすぎたら JPEG にする
    if (file.type === "image/png") {
      const png = await draw(bitmap, width, height, "image/png");
      if (png.size <= IMAGE_MAX_BYTES) return png;
    }
    const jpeg = await draw(bitmap, width, height, "image/jpeg");
    if (jpeg.size > IMAGE_MAX_BYTES) {
      throw new ImageError("画像が大きすぎます。別の画像でお試しください");
    }
    return jpeg;
  } finally {
    bitmap.close();
  }
}

async function draw(bitmap: ImageBitmap, width: number, height: number, type: string): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  if (!context) throw new ImageError("画像を読み込めませんでした");
  if (type === "image/jpeg") {
    // JPEG は透明を表せないので、透明な部分は黒ではなく白にする
    context.fillStyle = "#fff";
    context.fillRect(0, 0, width, height);
  }
  context.imageSmoothingQuality = "high";
  context.drawImage(bitmap, 0, 0, width, height);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, type, 0.85));
  if (!blob) throw new ImageError("画像を読み込めませんでした");
  return blob;
}
