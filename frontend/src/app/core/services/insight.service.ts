import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { InsightResponse } from '../models/insight.model';

/** `GET /api/exchange/insight` — contracts/insight.md. */
@Injectable({ providedIn: 'root' })
export class InsightService {
  private readonly http = inject(HttpClient);

  getInsight(
    from: string,
    to: string,
    fromDate: string,
    toDate: string,
  ): Observable<InsightResponse> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('fromDate', fromDate)
      .set('toDate', toDate);
    return this.http.get<InsightResponse>(`${environment.apiBaseUrl}/exchange/insight`, { params });
  }
}
