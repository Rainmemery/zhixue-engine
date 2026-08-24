"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { webSocketService, DataChangeEvent } from "@/services/websocketService";
import apiClient from "@/services/api";

interface SubmissionState {
  id: number;
  status: string;
  score: number;
  passedCount: number;
  totalCount: number;
  totalTimeMs: number | null;
  maxMemoryKb: number | null;
  errorMessage: string | null;
  judgedAt: string | null;
}

const FINAL_STATUSES = new Set([
  "accepted",
  "partial_accepted",
  "wrong_answer",
  "time_limit_exceeded",
  "memory_limit_exceeded",
  "runtime_error",
  "compilation_error",
  "system_error",
  "output_limit_exceeded",
]);

const MAX_CONSECUTIVE_FAILURES = 5;
const POLLING_INTERVAL = 2000;

export function useSubmissionStatus(submissionId: number | null) {
  const [submission, setSubmission] = useState<SubmissionState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);
  const consecutiveFailuresRef = useRef(0);
  const mountedRef = useRef(true);

  const isFinalStatus = useCallback((status: string): boolean => {
    return FINAL_STATUSES.has(status);
  }, []);

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  const fetchSubmission = useCallback(async () => {
    if (!submissionId) return;
    try {
      const res = await apiClient.get(`/submissions/${submissionId}`);
      if (!mountedRef.current) return;
      const json = res.data;
      if (json.code === 200 && json.data) {
        consecutiveFailuresRef.current = 0;
        setError(null);
        setSubmission(json.data);
        if (isFinalStatus(json.data.status)) {
          stopPolling();
        }
      } else {
        consecutiveFailuresRef.current++;
        console.error(
          "[useSubmissionStatus] Unexpected response:",
          json
        );
        if (consecutiveFailuresRef.current >= MAX_CONSECUTIVE_FAILURES) {
          setError("Failed to fetch submission status after multiple attempts");
          stopPolling();
        }
      }
    } catch (err) {
      if (!mountedRef.current) return;
      console.error("[useSubmissionStatus] Failed to fetch submission:", err);
      consecutiveFailuresRef.current++;
      if (consecutiveFailuresRef.current >= MAX_CONSECUTIVE_FAILURES) {
        setError("Failed to fetch submission status after multiple attempts");
        stopPolling();
      }
    }
  }, [submissionId, isFinalStatus, stopPolling]);

  useEffect(() => {
    mountedRef.current = true;

    if (!submissionId) {
      setSubmission(null);
      setError(null);
      return;
    }

    consecutiveFailuresRef.current = 0;
    setError(null);

    fetchSubmission();
    pollingRef.current = setInterval(fetchSubmission, POLLING_INTERVAL);

    const unsubscribe = webSocketService.onEntityType(
      "submission",
      (event: DataChangeEvent) => {
        if (event.entityId === String(submissionId) && event.data) {
          const data = event.data as SubmissionState;
          consecutiveFailuresRef.current = 0;
          setError(null);
          setSubmission(data);
          if (isFinalStatus(data.status)) {
            stopPolling();
          }
        }
      }
    );

    return () => {
      mountedRef.current = false;
      unsubscribe();
      stopPolling();
    };
  }, [submissionId, fetchSubmission, isFinalStatus, stopPolling]);

  const status = submission?.status ?? "pending";
  const isLoading = submissionId !== null && (status === "pending" || status === "judging");

  return { submission, status, isLoading, error };
}

export function useSubmissionListUpdates() {
  const [lastUpdate, setLastUpdate] = useState<DataChangeEvent | null>(null);

  useEffect(() => {
    const unsubscribe = webSocketService.onEntityType(
      "submission",
      (event: DataChangeEvent) => {
        setLastUpdate(event);
      }
    );

    return unsubscribe;
  }, []);

  return { lastUpdate };
}
