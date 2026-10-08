import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';

/** Preview kamera live (dicermin). Foto yang diambil juga dicermin agar sama dengan preview. */
export const LiveVideo = forwardRef<HTMLVideoElement | null, { stream: MediaStream | null }>(function LiveVideo(
  { stream },
  ref,
) {
  const inner = useRef<HTMLVideoElement>(null);
  useImperativeHandle(ref, () => inner.current as HTMLVideoElement, []);

  useEffect(() => {
    const video = inner.current;
    if (!video) return;
    if (video.srcObject !== stream) video.srcObject = stream;
    if (stream) video.play().catch(() => undefined);
  }, [stream]);

  return <video ref={inner} className="video mirrored" autoPlay muted playsInline data-testid="live-video" />;
});
