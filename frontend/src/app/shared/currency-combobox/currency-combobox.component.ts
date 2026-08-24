import { Component, Input, computed, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { CurrencyOption } from '../../core/models/currency.model';

/**
 * A `ControlValueAccessor` combobox for picking a currency code: typing
 * filters `options` by code or name, but the underlying value is always
 * exactly what's in the input — free-typing a code and never opening the
 * dropdown still works precisely as a plain `<input>` did before this
 * component existed. The dropdown is additive convenience, not a gate.
 */
@Component({
  selector: 'app-currency-combobox',
  imports: [],
  templateUrl: './currency-combobox.component.html',
  styleUrl: './currency-combobox.component.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CurrencyComboboxComponent),
      multi: true,
    },
  ],
})
export class CurrencyComboboxComponent implements ControlValueAccessor {
  @Input() inputId = '';
  @Input() placeholder = '';
  @Input({ required: true }) options: readonly CurrencyOption[] = [];

  protected readonly value = signal('');
  protected readonly open = signal(false);
  protected readonly activeIndex = signal(-1);
  protected readonly disabled = signal(false);

  protected readonly filteredOptions = computed(() => {
    const query = this.value().trim().toLowerCase();
    if (!query) {
      return this.options;
    }
    return this.options.filter(
      (option) =>
        option.code.toLowerCase().includes(query) || option.name.toLowerCase().includes(query),
    );
  });

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected onInput(raw: string): void {
    this.value.set(raw);
    this.onChange(raw);
    this.open.set(true);
    this.activeIndex.set(-1);
  }

  protected onFocus(): void {
    this.open.set(true);
  }

  protected onBlur(): void {
    this.open.set(false);
    this.onTouched();
  }

  protected onOptionMouseDown(event: MouseEvent, option: CurrencyOption): void {
    // Keeps focus on the input (no blur fires), so selecting with the mouse
    // doesn't race the dropdown closing out from under the click.
    event.preventDefault();
    this.select(option);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const items = this.filteredOptions();

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        this.activeIndex.set(Math.min(this.activeIndex() + 1, items.length - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.set(Math.max(this.activeIndex() - 1, 0));
        break;
      case 'Enter': {
        const active = items[this.activeIndex()];
        if (this.open() && active) {
          event.preventDefault();
          this.select(active);
        }
        break;
      }
      case 'Escape':
        this.open.set(false);
        this.activeIndex.set(-1);
        break;
    }
  }

  private select(option: CurrencyOption): void {
    this.value.set(option.code);
    this.onChange(option.code);
    this.open.set(false);
    this.activeIndex.set(-1);
  }
}
