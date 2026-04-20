'use client';

import { useEffect, useState } from 'react';
import { CheckCircle2, XCircle, AlertCircle } from 'lucide-react';

interface FormValidatorProps {
  formData: any;
  validationRules?: Record<string, string>;
  onValidationChange?: (isValid: boolean, errors: Record<string, string[]>) => void;
}

export function FormValidator({
  formData,
  validationRules,
  onValidationChange,
}: FormValidatorProps) {
  const [errors, setErrors] = useState<Record<string, string[]>>({});
  const [isValid, setIsValid] = useState(true);

  useEffect(() => {
    if (!validationRules || !formData) {
      setIsValid(true);
      setErrors({});
      onValidationChange?.(true, {});
      return;
    }

    const newErrors: Record<string, string[]> = {};

    Object.entries(validationRules).forEach(([field, rules]) => {
      const fieldErrors: string[] = [];
      const value = formData[field];
      const rulesList = rules.split(',').map((r) => r.trim());

      rulesList.forEach((rule) => {
        // Required validation
        if (rule === 'required' && !value) {
          fieldErrors.push(`${field} is required`);
        }

        // Email validation
        if (rule === 'email' && value) {
          const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
          if (!emailRegex.test(value)) {
            fieldErrors.push(`${field} must be a valid email`);
          }
        }

        // Min length validation
        if (rule.startsWith('minLength:') && value) {
          const minLength = parseInt(rule.split(':')[1]);
          if (value.length < minLength) {
            fieldErrors.push(`${field} must be at least ${minLength} characters`);
          }
        }

        // Max length validation
        if (rule.startsWith('maxLength:') && value) {
          const maxLength = parseInt(rule.split(':')[1]);
          if (value.length > maxLength) {
            fieldErrors.push(`${field} must not exceed ${maxLength} characters`);
          }
        }

        // Min value validation
        if (rule.startsWith('min:') && value !== undefined) {
          const min = parseFloat(rule.split(':')[1]);
          if (parseFloat(value) < min) {
            fieldErrors.push(`${field} must be at least ${min}`);
          }
        }

        // Max value validation
        if (rule.startsWith('max:') && value !== undefined) {
          const max = parseFloat(rule.split(':')[1]);
          if (parseFloat(value) > max) {
            fieldErrors.push(`${field} must not exceed ${max}`);
          }
        }

        // Pattern validation
        if (rule.startsWith('pattern:') && value) {
          const pattern = rule.split(':')[1];
          const regex = new RegExp(pattern);
          if (!regex.test(value)) {
            fieldErrors.push(`${field} format is invalid`);
          }
        }

        // Date validation
        if (rule === 'date' && value) {
          const date = new Date(value);
          if (isNaN(date.getTime())) {
            fieldErrors.push(`${field} must be a valid date`);
          }
        }

        // In (enum) validation
        if (rule.startsWith('in:') && value) {
          const allowedValues = rule.split(':')[1].split('|');
          if (!allowedValues.includes(value)) {
            fieldErrors.push(`${field} must be one of: ${allowedValues.join(', ')}`);
          }
        }
      });

      if (fieldErrors.length > 0) {
        newErrors[field] = fieldErrors;
      }
    });

    const valid = Object.keys(newErrors).length === 0;
    setErrors(newErrors);
    setIsValid(valid);
    onValidationChange?.(valid, newErrors);
  }, [formData, validationRules, onValidationChange]);

  if (!validationRules || Object.keys(errors).length === 0) {
    return null;
  }

  return (
    <div className="space-y-2">
      {Object.entries(errors).map(([field, fieldErrors]) => (
        <div key={field} className="bg-red-50 border border-red-200 rounded-md p-3">
          <div className="flex items-start">
            <XCircle className="h-5 w-5 text-red-600 mt-0.5 mr-2 flex-shrink-0" />
            <div>
              <h4 className="text-sm font-medium text-red-800">{field}</h4>
              <ul className="mt-1 text-sm text-red-700 list-disc list-inside">
                {fieldErrors.map((error, idx) => (
                  <li key={idx}>{error}</li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function ValidationSummary({
  isValid,
  errorCount,
}: {
  isValid: boolean;
  errorCount: number;
}) {
  if (isValid) {
    return (
      <div className="bg-green-50 border border-green-200 rounded-md p-3">
        <div className="flex items-center">
          <CheckCircle2 className="h-5 w-5 text-green-600 mr-2" />
          <p className="text-sm font-medium text-green-800">All validations passed</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-yellow-50 border border-yellow-200 rounded-md p-3">
      <div className="flex items-center">
        <AlertCircle className="h-5 w-5 text-yellow-600 mr-2" />
        <p className="text-sm font-medium text-yellow-800">
          {errorCount} validation error{errorCount !== 1 ? 's' : ''} found
        </p>
      </div>
    </div>
  );
}
