import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { CurrencyOption } from '../../core/models/currency.model';
import { CurrencyComboboxComponent } from './currency-combobox.component';

const OPTIONS: readonly CurrencyOption[] = [
  { code: 'EUR', name: 'Euro' },
  { code: 'USD', name: 'US Dollar' },
  { code: 'GBP', name: 'British Pound' },
];

/**
 * A minimal host exercises the component exactly as production code will:
 * bound via `[formControl]`, not by reaching into the combobox's own
 * internals — same convention as CalculatorComponent.spec.ts.
 */
@Component({
  imports: [ReactiveFormsModule, CurrencyComboboxComponent],
  template: `<app-currency-combobox
    inputId="from"
    placeholder="EUR"
    [options]="options"
    [formControl]="control"
  />`,
})
class HostComponent {
  readonly options = OPTIONS;
  readonly control = new FormControl('');
}

describe('CurrencyComboboxComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('#from');
  }

  function options(): HTMLLIElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.combobox-option'));
  }

  function typeInto(value: string): void {
    const el = input();
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('is closed until focused, then lists every option', () => {
    expect(fixture.nativeElement.querySelector('.combobox-panel')).toBeFalsy();

    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    const codes = options().map((el) => el.querySelector('.option-code')?.textContent);
    const names = options().map((el) => el.querySelector('.option-name')?.textContent);
    expect(codes).toEqual(['EUR', 'USD', 'GBP']);
    expect(names).toEqual(['Euro', 'US Dollar', 'British Pound']);
  });

  it('filters by code or by name, case-insensitively', () => {
    input().dispatchEvent(new Event('focus'));
    typeInto('us');

    expect(options().map((el) => el.querySelector('.option-code')?.textContent)).toEqual(['USD']);

    typeInto('pound');

    expect(options().map((el) => el.querySelector('.option-code')?.textContent)).toEqual(['GBP']);
  });

  it('free-typing a value updates the bound form control exactly as typed, without requiring a selection', () => {
    typeInto('xyz');

    expect(host.control.value).toBe('xyz');
  });

  it('selecting an option with the mouse sets the code and closes the dropdown', () => {
    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    options()[1].dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(host.control.value).toBe('USD');
    expect(input().value).toBe('USD');
    expect(fixture.nativeElement.querySelector('.combobox-panel')).toBeFalsy();
  });

  it('ArrowDown then Enter selects the active option', () => {
    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    // Two ArrowDowns from index -1 land on index 1 (USD).
    expect(host.control.value).toBe('USD');
  });

  it('closes on blur', () => {
    input().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.combobox-panel')).toBeTruthy();

    input().dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.combobox-panel')).toBeFalsy();
  });

  it('writeValue (external form control set) is reflected in the input', () => {
    host.control.setValue('GBP');
    fixture.detectChanges();

    expect(input().value).toBe('GBP');
  });
});
