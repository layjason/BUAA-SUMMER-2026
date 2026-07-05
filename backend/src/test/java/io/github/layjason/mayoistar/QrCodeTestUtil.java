package io.github.layjason.mayoistar;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;

/**
 * 二维码解码测试工具。
 *
 * <p>类职责：提供从 PNG 字节数组中鲁棒地解码二维码文本的方法，处理不同环境（如 headless CI）下 ImageIO
 * 行为差异导致的 ZXing HybridBinarizer 偶发解码失败。
 *
 * <p>后置条件：从 PNG 中解码出二维码文本；若 HybridBinarizer 失败，自动回退到 GlobalHistogramBinarizer。
 */
@UtilityClass
public class QrCodeTestUtil {

    /**
     * 从 PNG 二维码图片字节数组中解码出文本内容。
     *
     * <p>前置条件：pngBytes 为有效的 PNG 二维码图片。
     *
     * <p>后置条件：返回解码出的文本；若所有 binarizer 均失败，抛出 RuntimeException。
     */
    public static String decodeQrCodeFromPng(byte[] pngBytes) {
        BufferedImage image = readPng(pngBytes);
        Map<DecodeHintType, Object> hints = createDecodeHints();

        try {
            return decodeWithHybridBinarizer(image, hints);
        } catch (ReaderException e) {
            try {
                return decodeWithGlobalHistogramBinarizer(image, hints);
            } catch (ReaderException e2) {
                try {
                    return decodePureBarcode(image, hints);
                } catch (ReaderException e3) {
                    try {
                        return decodeMultiple(image, hints);
                    } catch (ReaderException e4) {
                        throw new RuntimeException("QR code decoding failed after all fallbacks", e);
                    }
                }
            }
        }
    }

    /**
     * 创建二维码测试解码参数。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回仅查找 QR_CODE 且启用 TRY_HARDER 的 ZXing hints。
     */
    private static Map<DecodeHintType, Object> createDecodeHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return hints;
    }

    /**
     * 使用 HybridBinarizer 解码二维码。
     *
     * <p>前置条件：image 为有效二维码图片。
     *
     * <p>后置条件：返回二维码文本；若未识别到二维码则抛出 ReaderException。
     */
    private static String decodeWithHybridBinarizer(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws ReaderException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new MultiFormatReader().decode(bitmap, hints).getText();
    }

    /**
     * 使用 GlobalHistogramBinarizer 解码二维码。
     *
     * <p>前置条件：image 为有效二维码图片。
     *
     * <p>后置条件：返回二维码文本；若未识别到二维码则抛出 ReaderException。
     */
    private static String decodeWithGlobalHistogramBinarizer(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws ReaderException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
        return new MultiFormatReader().decode(bitmap, hints).getText();
    }

    /**
     * 以纯二维码图片模式解码二维码。
     *
     * <p>前置条件：image 为服务生成的单一二维码图片。
     *
     * <p>后置条件：返回二维码文本；若未识别到二维码则抛出 ReaderException。
     */
    private static String decodePureBarcode(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws ReaderException {
        Map<DecodeHintType, Object> pureHints = new EnumMap<>(hints);
        pureHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new QRCodeReader().decode(bitmap, pureHints).getText();
    }

    /**
     * 使用多二维码读取器兜底解码二维码。
     *
     * <p>前置条件：image 为有效二维码图片。
     *
     * <p>后置条件：返回第一个二维码文本；若未识别到二维码则抛出 ReaderException。
     */
    private static String decodeMultiple(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws ReaderException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        GenericMultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(new QRCodeReader());
        Result[] results = multiReader.decodeMultiple(bitmap, hints);
        if (results.length > 0) {
            return results[0].getText();
        }
        throw NotFoundException.getNotFoundInstance();
    }

    private static BufferedImage readPng(byte[] pngBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                throw new RuntimeException("ImageIO.read returned null for PNG data");
            }
            return image;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PNG image", e);
        }
    }
}
