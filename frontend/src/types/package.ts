/** Minimal course reference as embedded in a package. */
export interface PackageCourseRef {
  id: number;
  name: string;
}

export interface PackageResponse {
  id: number;
  name: string;
  price: number;
  durationMonths: number;
  active: boolean;
  courses: PackageCourseRef[];
}

export interface PackageRequest {
  name: string;
  price: number;
  durationMonths: number;
  active?: boolean;
  courseIds: number[];
}
