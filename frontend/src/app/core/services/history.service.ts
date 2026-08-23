import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { HistoryResponse } from '../models/history.model';

/** `GET /api/exchange/history` — contracts/exchange.md. */
@Injectable({ providedIn: 'root' })
export class HistoryService {
  private readonly http = inject(HttpClient);

  getHistory(
    from: string,
    to: string,
    startDate: string,
    endDate: string,
  ): Observable<HistoryResponse> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get<HistoryResponse>(`${environment.apiBaseUrl}/exchange/history`, { params });
  }
}
