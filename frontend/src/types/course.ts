/** Minimal discipline reference as embedded in a course. */
export interface CourseDisciplineRef {
  id: number;
  name: string;
}

/** Minimal trainer reference as embedded in a course. */
export interface CourseTrainerRef {
  id: number;
  fullName: string;
}

export interface CourseResponse {
  id: number;
  name: string;
  level: string | null;
  discipline: CourseDisciplineRef;
  trainer: CourseTrainerRef | null;
  /** ISO days the course runs on, ascending. 1 = lunedì .. 7 = domenica. */
  daysOfWeek: number[];
  /**
   * @deprecated Legacy mirror of the first entry of `daysOfWeek`, still sent by the backend
   * for clients not yet updated. Read `daysOfWeek`.
   */
  dayOfWeek: number;
  /** "HH:mm" */
  startTime: string;
  durationMin: number;
  capacity: number | null;
  active: boolean;
}

export interface CourseRequest {
  name: string;
  disciplineId: number;
  level?: string | null;
  trainerId?: number | null;
  /** One to seven ISO days (1 = lunedì). The backend rejects an empty list with a 422. */
  daysOfWeek: number[];
  startTime: string;
  durationMin: number;
  capacity?: number | null;
  active?: boolean;
}
