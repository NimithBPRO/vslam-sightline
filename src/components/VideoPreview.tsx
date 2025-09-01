import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { Camera, AlertCircle, Wifi, WifiOff } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface VideoPreviewProps {
  selectedCamera: string;
  isScanning: boolean;
  arCoreEnabled: boolean;
}

export function VideoPreview({ selectedCamera, isScanning, arCoreEnabled }: VideoPreviewProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [error, setError] = useState<string>("");
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    if (selectedCamera) {
      startVideo();
    }
    
    return () => {
      stopVideo();
    };
  }, [selectedCamera]);

  const startVideo = async () => {
    try {
      setError("");
      const constraints = {
        video: {
          deviceId: selectedCamera ? { exact: selectedCamera } : undefined,
          width: { ideal: 1280 },
          height: { ideal: 720 },
          frameRate: { ideal: 30 }
        }
      };

      const mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      setStream(mediaStream);
      setIsConnected(true);

      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
      }
    } catch (err) {
      setError(`Failed to access camera: ${err instanceof Error ? err.message : 'Unknown error'}`);
      setIsConnected(false);
    }
  };

  const stopVideo = () => {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
      setIsConnected(false);
    }
  };

  return (
    <div className="tech-panel-elevated h-full flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <Camera className="h-5 w-5 text-tech-cyan" />
          <h3 className="font-semibold">Live Feed</h3>
          {arCoreEnabled && (
            <span className="px-2 py-1 text-xs bg-tech-amber text-black rounded-full font-medium">
              ARCore
            </span>
          )}
        </div>
        
        <div className="flex items-center space-x-2">
          {isConnected ? (
            <Wifi className="h-4 w-4 text-tech-green" />
          ) : (
            <WifiOff className="h-4 w-4 text-tech-red" />
          )}
          <span className="text-xs text-muted-foreground">
            {isConnected ? "Connected" : "Disconnected"}
          </span>
        </div>
      </div>

      <div className="relative flex-1 min-h-[400px] bg-surface rounded-lg overflow-hidden">
        {error ? (
          <div className="absolute inset-0 flex items-center justify-center">
            <Alert className="border-destructive/50 text-destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          </div>
        ) : (
          <>
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="w-full h-full object-cover"
            />
            
            {/* Scanning Overlay */}
            {isScanning && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="absolute inset-0 pointer-events-none"
              >
                {/* Scanning Lines */}
                <div className="absolute inset-0">
                  <motion.div
                    className="absolute w-full h-0.5 bg-gradient-to-r from-transparent via-tech-cyan to-transparent"
                    animate={{ y: ["0%", "100%", "0%"] }}
                    transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                  />
                  <motion.div
                    className="absolute h-full w-0.5 bg-gradient-to-b from-transparent via-tech-cyan to-transparent"
                    animate={{ x: ["0%", "100%", "0%"] }}
                    transition={{ duration: 4, repeat: Infinity, ease: "linear" }}
                  />
                </div>
                
                {/* Corner Markers */}
                <div className="absolute top-4 left-4 w-8 h-8 border-l-2 border-t-2 border-tech-cyan animate-pulse-glow" />
                <div className="absolute top-4 right-4 w-8 h-8 border-r-2 border-t-2 border-tech-cyan animate-pulse-glow" />
                <div className="absolute bottom-4 left-4 w-8 h-8 border-l-2 border-b-2 border-tech-cyan animate-pulse-glow" />
                <div className="absolute bottom-4 right-4 w-8 h-8 border-r-2 border-b-2 border-tech-cyan animate-pulse-glow" />
                
                {/* Status Text */}
                <div className="absolute top-4 left-1/2 transform -translate-x-1/2">
                  <motion.div
                    initial={{ scale: 0.9 }}
                    animate={{ scale: 1 }}
                    className="bg-black/80 px-3 py-1 rounded-full border border-tech-cyan"
                  >
                    <span className="text-tech-cyan text-sm font-medium animate-pulse-glow">
                      SCANNING...
                    </span>
                  </motion.div>
                </div>
              </motion.div>
            )}

            {/* ARCore Overlay */}
            {arCoreEnabled && (
              <div className="absolute top-4 right-4">
                <motion.div
                  animate={{ scale: [1, 1.1, 1] }}
                  transition={{ duration: 2, repeat: Infinity }}
                  className="bg-gradient-accent px-3 py-1 rounded-full border border-tech-amber"
                >
                  <span className="text-black text-xs font-bold">AR MODE</span>
                </motion.div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}