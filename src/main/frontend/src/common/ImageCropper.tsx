import React, { useEffect, useRef, useState } from "react";
import { Grid, Modal, Slider, Space, Typography } from "antd";
import { getRes } from "../utils/constants";
import styled from "styled-components";
import { useTheme } from "antd-style";

const { Text } = Typography;

const Stage = styled.div<{ $height: number; $background: string }>`
    position: relative;
    width: 100%;
    height: ${(props) => props.$height}px;
    background: ${(props) => props.$background};
    overflow: hidden;
    display: flex;
    justify-content: center;
    align-items: center;
    cursor: grab;
    user-select: none;

    &:active {
        cursor: grabbing;
    }
`;

const CropOverlay = styled.div<{ $width: number; $height: number; $border: string; $mask: string }>`
    position: absolute;
    width: ${(props) => props.$width}px;
    height: ${(props) => props.$height}px;
    border: ${(props) => props.$border};
    box-shadow: 0 0 0 9999px ${(props) => props.$mask};
    pointer-events: none;
    z-index: 10;
`;

interface ImageCropperProps {
    open: boolean;
    onCancel: () => void;
    onOk: (dataUrl: string) => void;
    onError?: (message: string) => void;
    imageUrl: string;
    resolveImageUrl?: (url: string) => string;
    aspectRatio?: number; // width / height
}

const ImageCropper: React.FC<ImageCropperProps> = ({
    open,
    onCancel,
    onOk,
    onError,
    imageUrl,
    resolveImageUrl = (url) => url,
    aspectRatio = 16 / 9,
}) => {
    const [zoom, setZoom] = useState(1);
    const [minZoom, setMinZoom] = useState(1);
    const [offset, setOffset] = useState({ x: 0, y: 0 });
    const [imageSize, setImageSize] = useState({ width: 0, height: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
    const [renderImageUrl, setRenderImageUrl] = useState(imageUrl);
    const [canExportImage, setCanExportImage] = useState(false);
    const imgRef = useRef<HTMLImageElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
    const narrow = screens.md !== true;
    const cropBorder = `${theme.lineWidth * 2}px ${theme.lineType} ${theme.colorPrimary}`;

    const roundZoom = (value: number) => Math.ceil(value * 10) / 10;

    const maxFrameWidth = narrow ? 280 : 440;
    const maxFrameHeight = narrow ? 240 : 320;
    const frameWidth = Math.round(
        aspectRatio >= maxFrameWidth / maxFrameHeight ? maxFrameWidth : maxFrameHeight * aspectRatio
    );
    const frameHeight = Math.round(frameWidth / aspectRatio);
    const stageHeight = Math.round(frameHeight + 48);

    const clampOffset = (nextOffset: { x: number; y: number }, nextZoom = zoom) => {
        if (!imageSize.width || !imageSize.height) {
            return nextOffset;
        }
        const imgWidth = imageSize.width * nextZoom;
        const imgHeight = imageSize.height * nextZoom;
        const maxX = Math.max(0, (imgWidth - frameWidth) / 2);
        const maxY = Math.max(0, (imgHeight - frameHeight) / 2);
        return {
            x: Math.min(maxX, Math.max(-maxX, nextOffset.x)),
            y: Math.min(maxY, Math.max(-maxY, nextOffset.y)),
        };
    };

    useEffect(() => {
        if (open) {
            setZoom(1);
            setOffset({ x: 0, y: 0 });
            setMinZoom(1);
            setImageSize({ width: 0, height: 0 });
        }
    }, [open, imageUrl]);

    useEffect(() => {
        if (!open || !imageUrl) {
            setRenderImageUrl(imageUrl);
            setCanExportImage(false);
            return;
        }
        if (imageUrl.startsWith("data:") || imageUrl.startsWith("blob:")) {
            setRenderImageUrl(imageUrl);
            setCanExportImage(true);
            return;
        }

        const controller = new AbortController();
        let objectUrl: string | undefined;
        setRenderImageUrl("");
        setCanExportImage(false);
        fetch(resolveImageUrl(imageUrl), { credentials: "include", signal: controller.signal })
            .then((response) => {
                if (!response.ok) {
                    throw new Error(response.statusText);
                }
                return response.blob();
            })
            .then((blob) => {
                if (controller.signal.aborted) {
                    return;
                }
                objectUrl = URL.createObjectURL(blob);
                setRenderImageUrl(objectUrl);
                setCanExportImage(true);
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setRenderImageUrl(imageUrl);
                    setCanExportImage(false);
                }
            });

        return () => {
            controller.abort();
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
        };
    }, [open, imageUrl, resolveImageUrl]);

    useEffect(() => {
        setOffset((current) => clampOffset(current));
    }, [zoom, frameHeight]);

    const handleImageLoad = () => {
        const img = imgRef.current;
        const container = containerRef.current;
        if (!img || !container) {
            return;
        }
        const containerWidth = container.clientWidth;
        const containerHeight = container.clientHeight;
        const fitScale = Math.min(containerWidth / img.naturalWidth, containerHeight / img.naturalHeight);
        const baseWidth = Math.round(img.naturalWidth * fitScale);
        const baseHeight = Math.round(img.naturalHeight * fitScale);
        const nextMinZoom = roundZoom(Math.max(frameWidth / baseWidth, frameHeight / baseHeight));
        setImageSize({ width: baseWidth, height: baseHeight });
        setMinZoom(nextMinZoom);
        setZoom(nextMinZoom);
        setOffset({ x: 0, y: 0 });
    };

    const handleMouseDown = (e: React.MouseEvent) => {
        setIsDragging(true);
        setDragStart({ x: e.clientX - offset.x, y: e.clientY - offset.y });
    };

    const handleMouseMove = (e: React.MouseEvent) => {
        if (!isDragging) return;
        setOffset(
            clampOffset({
                x: e.clientX - dragStart.x,
                y: e.clientY - dragStart.y,
            })
        );
    };

    const handleMouseUp = () => {
        setIsDragging(false);
    };

    const handleOk = () => {
        const img = imgRef.current;
        const container = containerRef.current;
        if (!img || !container || !imageSize.width || !imageSize.height) return;
        if (!canExportImage) {
            onError?.(getRes().articleEdit.assistant.cropExportFailed);
            return;
        }

        const canvas = document.createElement("canvas");
        canvas.width = 1200; // 高清输出
        canvas.height = canvas.width / aspectRatio;

        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        // 计算图片在 canvas 上的绘制参数
        // 目标是将可视区域（Frame 内部）映射到整个 canvas

        // 可视区域中心相对于图片中心的偏移（容器坐标系）
        // offset 是图片相对于容器中心的偏移
        // 我们需要找到图片左上角相对于 Frame 左上角的坐标
        const frameLeft = (container.clientWidth - frameWidth) / 2;
        const frameTop = (container.clientHeight - frameHeight) / 2;

        const imgWidth = imageSize.width * zoom;
        const imgHeight = imageSize.height * zoom;

        const imgLeftInContainer = (container.clientWidth - imgWidth) / 2 + offset.x;
        const imgTopInContainer = (container.clientHeight - imgHeight) / 2 + offset.y;

        const sourceX = ((frameLeft - imgLeftInContainer) / zoom) * (img.naturalWidth / imageSize.width);
        const sourceY = ((frameTop - imgTopInContainer) / zoom) * (img.naturalHeight / imageSize.height);
        const sourceWidth = (frameWidth / zoom) * (img.naturalWidth / imageSize.width);
        const sourceHeight = (frameHeight / zoom) * (img.naturalHeight / imageSize.height);

        ctx.drawImage(img, sourceX, sourceY, sourceWidth, sourceHeight, 0, 0, canvas.width, canvas.height);

        try {
            onOk(canvas.toDataURL("image/png"));
        } catch (e) {
            onError?.(getRes().articleEdit.assistant.cropExportFailed);
        }
    };

    return (
        <Modal
            title={getRes().articleEdit.assistant.crop}
            open={open}
            onCancel={onCancel}
            onOk={handleOk}
            okButtonProps={{ disabled: !canExportImage || !imageSize.width || !imageSize.height }}
            width={narrow ? "calc(100vw - 32px)" : 600}
            destroyOnClose
            centered
        >
            <div style={{ marginBottom: 16 }}>
                <Stage
                    ref={containerRef}
                    $height={stageHeight}
                    $background={theme.colorFillSecondary}
                    onMouseDown={handleMouseDown}
                    onMouseMove={handleMouseMove}
                    onMouseUp={handleMouseUp}
                    onMouseLeave={handleMouseUp}
                >
                    {renderImageUrl && (
                        <img
                            ref={imgRef}
                            src={renderImageUrl}
                            alt={getRes().articleEdit.assistant.cropImageAlt}
                            draggable={false}
                            onLoad={handleImageLoad}
                            style={{
                                transform: `translate(${offset.x}px, ${offset.y}px) scale(${zoom})`,
                                width: imageSize.width ? imageSize.width : undefined,
                                height: imageSize.height ? imageSize.height : undefined,
                                maxWidth: imageSize.width ? undefined : "100%",
                                maxHeight: imageSize.height ? undefined : "100%",
                                transition: isDragging ? "none" : "transform 0.1s",
                            }}
                        />
                    )}
                    <CropOverlay
                        $width={frameWidth}
                        $height={frameHeight}
                        $border={cropBorder}
                        $mask={theme.colorBgMask}
                    />
                </Stage>
            </div>
            <Space direction="vertical" style={{ width: "100%" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <Text type="secondary">{getRes().articleEdit.assistant.cropZoom}</Text>
                    <Slider
                        min={minZoom}
                        max={Math.max(5, minZoom)}
                        step={0.1}
                        value={zoom}
                        tooltip={{ formatter: (value) => `${Number(value || 0).toFixed(1)}x` }}
                        onChange={(v) => setZoom(roundZoom(v))}
                        style={{ flex: 1 }}
                    />
                </div>
                <Text type="secondary">{getRes().articleEdit.assistant.cropDragTip}</Text>
                {renderImageUrl && !canExportImage && (
                    <Text type="danger">{getRes().articleEdit.assistant.cropExportFailed}</Text>
                )}
            </Space>
        </Modal>
    );
};

export default ImageCropper;
