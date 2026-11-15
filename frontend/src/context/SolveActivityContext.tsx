import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useMatch } from "react-router-dom";

export type SolveActivityView = "parsons" | "aiCode";

interface SolveActivityContextValue {
    activityView: SolveActivityView;
    setActivityView: (view: SolveActivityView) => void;
}

const SolveActivityContext = createContext<SolveActivityContextValue | null>(null);

export function SolveActivityProvider({ children }: { children: ReactNode }) {
    const [activityView, setActivityView] = useState<SolveActivityView>("parsons");
    const matchSolve = useMatch("/solve/:taskId");

    useEffect(() => {
        if (!matchSolve) {
            setActivityView("parsons");
        }
    }, [matchSolve, matchSolve?.params.taskId]);

    return (
        <SolveActivityContext.Provider value={{ activityView, setActivityView }}>
            {children}
        </SolveActivityContext.Provider>
    );
}

export function useSolveActivity(): SolveActivityContextValue {
    const ctx = useContext(SolveActivityContext);
    if (!ctx) {
        throw new Error("useSolveActivity must be used within SolveActivityProvider");
    }
    return ctx;
}