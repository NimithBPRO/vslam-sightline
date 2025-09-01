import { useState } from "react";
import { motion } from "framer-motion";
import { Camera, Smartphone, CheckCircle, AlertTriangle, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface ARCoreModalProps {
  isOpen: boolean;
  onClose: () => void;
  onPermissionsGranted: () => void;
}

export function ARCoreModal({ isOpen, onClose, onPermissionsGranted }: ARCoreModalProps) {
  const [permissionStep, setPermissionStep] = useState<'intro' | 'requesting' | 'success' | 'error'>('intro');
  const [errorMessage, setErrorMessage] = useState<string>("");

  const requestPermissions = async () => {
    setPermissionStep('requesting');
    setErrorMessage("");

    try {
      // Request camera permission
      await navigator.mediaDevices.getUserMedia({ 
        video: { 
          facingMode: 'environment' // Prefer rear camera for AR
        } 
      });

      // Request motion sensors (if supported)
      if ('DeviceMotionEvent' in window && typeof (DeviceMotionEvent as any).requestPermission === 'function') {
        await (DeviceMotionEvent as any).requestPermission();
      }

      if ('DeviceOrientationEvent' in window && typeof (DeviceOrientationEvent as any).requestPermission === 'function') {
        await (DeviceOrientationEvent as any).requestPermission();
      }

      setPermissionStep('success');
      setTimeout(() => {
        onPermissionsGranted();
      }, 1500);

    } catch (error) {
      setErrorMessage(`Permission denied: ${error instanceof Error ? error.message : 'Unknown error'}`);
      setPermissionStep('error');
    }
  };

  const resetAndClose = () => {
    setPermissionStep('intro');
    setErrorMessage("");
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={resetAndClose}>
      <DialogContent className="sm:max-w-md bg-surface-elevated border-tech/30">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2 text-gradient-primary">
            <Smartphone className="h-5 w-5" />
            <span>Enable ARCore Mode</span>
          </DialogTitle>
          <DialogDescription className="text-muted-foreground">
            ARCore requires access to your camera and motion sensors for enhanced tracking.
          </DialogDescription>
        </DialogHeader>

        <div className="py-4">
          {permissionStep === 'intro' && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="space-y-4"
            >
              <div className="space-y-3">
                <div className="flex items-start space-x-3 p-3 rounded-lg bg-surface/50">
                  <Camera className="h-5 w-5 text-tech-cyan mt-0.5" />
                  <div>
                    <h4 className="text-sm font-medium">Camera Access</h4>
                    <p className="text-xs text-muted-foreground">
                      Required for visual tracking and environment mapping
                    </p>
                  </div>
                </div>

                <div className="flex items-start space-x-3 p-3 rounded-lg bg-surface/50">
                  <Smartphone className="h-5 w-5 text-tech-amber mt-0.5" />
                  <div>
                    <h4 className="text-sm font-medium">Motion Sensors</h4>
                    <p className="text-xs text-muted-foreground">
                      Accelerometer and gyroscope for improved tracking accuracy
                    </p>
                  </div>
                </div>
              </div>

              <Alert className="border-tech/30">
                <AlertTriangle className="h-4 w-4" />
                <AlertDescription className="text-sm">
                  Your browser will prompt for these permissions. Please allow access for ARCore to function properly.
                </AlertDescription>
              </Alert>
            </motion.div>
          )}

          {permissionStep === 'requesting' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center py-8"
            >
              <div className="animate-spin w-8 h-8 border-2 border-tech-cyan border-t-transparent rounded-full mx-auto mb-4" />
              <p className="text-sm text-muted-foreground">
                Requesting permissions...
              </p>
            </motion.div>
          )}

          {permissionStep === 'success' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center py-8"
            >
              <CheckCircle className="w-12 h-12 text-tech-green mx-auto mb-4" />
              <h4 className="text-lg font-semibold text-tech-green mb-2">
                Permissions Granted!
              </h4>
              <p className="text-sm text-muted-foreground">
                ARCore mode is now enabled
              </p>
            </motion.div>
          )}

          {permissionStep === 'error' && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="space-y-4"
            >
              <Alert className="border-destructive/50 text-destructive">
                <AlertTriangle className="h-4 w-4" />
                <AlertDescription>{errorMessage}</AlertDescription>
              </Alert>
              
              <p className="text-sm text-muted-foreground">
                Please ensure you're using HTTPS and grant the requested permissions to enable ARCore mode.
              </p>
            </motion.div>
          )}
        </div>

        <div className="flex justify-end space-x-2 pt-4 border-t border-border/50">
          <Button variant="outline" onClick={resetAndClose}>
            <X className="h-4 w-4 mr-2" />
            Cancel
          </Button>
          
          {permissionStep === 'intro' && (
            <Button onClick={requestPermissions} className="bg-gradient-primary hover:opacity-90">
              Grant Permissions
            </Button>
          )}
          
          {permissionStep === 'error' && (
            <Button onClick={requestPermissions} className="bg-gradient-primary hover:opacity-90">
              Try Again
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}