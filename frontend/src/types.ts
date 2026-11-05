// TypeScript versions of the JSON the backend sends (the DTO records in com.handson.dto).
// If a field name here doesn't match the API, the compiler flags every place that uses it.

export type Role = "COORDINATOR" | "VOLUNTEER";

export type SignupStatus = "CONFIRMED" | "WAITLISTED" | "CANCELLED" | "ATTENDED" | "NO_SHOW";

export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
}

export interface Signup {
  id: number;
  shiftId: number;
  status: SignupStatus;
  waitlistPosition: number | null;
}

export interface Shift {
  id: number;
  eventId: number;
  title: string;
  startTime: string; // "2026-12-05T08:00:00", the organization's local time
  endTime: string;
  capacity: number;
  confirmedCount: number;
  waitlistCount: number;
  spotsLeft: number;
  mySignup: Signup | null;
}

export interface EventDetails {
  id: number;
  title: string;
  description: string | null;
  location: string;
  shifts: Shift[];
}

export interface MySignup {
  signupId: number;
  status: SignupStatus;
  waitlistPosition: number | null;
  shiftId: number;
  shiftTitle: string;
  startTime: string;
  endTime: string;
  eventId: number;
  eventTitle: string;
  location: string;
}

export interface RosterEntry {
  signupId: number;
  userId: number;
  fullName: string;
  email: string;
  status: SignupStatus;
  waitlistPosition: number | null;
}

export interface VolunteerHours {
  userId: number;
  fullName: string;
  email: string;
  shiftsAttended: number;
  totalHours: number;
}

export interface MyHours {
  shiftsAttended: number;
  totalHours: number;
}

/** The body of every error response (ApiError.java). */
export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  fieldErrors: Record<string, string>;
  timestamp: string;
}
