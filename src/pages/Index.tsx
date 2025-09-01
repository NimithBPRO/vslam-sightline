import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { VSLAMDashboard } from "../components/VSLAMDashboard";

const Index = () => {
  console.log("Index component rendering...");
  try {
    return <VSLAMDashboard />;
  } catch (error) {
    console.error("Error rendering VSLAMDashboard:", error);
    return <div>Error loading dashboard: {String(error)}</div>;
  }
};

export default Index;
