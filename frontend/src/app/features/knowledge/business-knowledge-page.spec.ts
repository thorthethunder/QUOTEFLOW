import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { BusinessKnowledgeApiService } from './business-knowledge-api.service';
import { BusinessKnowledgePageComponent } from './business-knowledge-page';

describe('BusinessKnowledgePageComponent', () => {
  let fixture: ComponentFixture<BusinessKnowledgePageComponent>;
  let api: jasmine.SpyObj<BusinessKnowledgeApiService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<BusinessKnowledgeApiService>('BusinessKnowledgeApiService', [
      'list',
      'createText',
      'uploadText',
      'delete',
      'ask',
    ]);
    api.list.and.returnValue(of([]));
    api.createText.and.returnValue(
      of({
        id: 'doc-1',
        title: 'Terms',
        sourceType: 'TEXT',
        status: 'READY',
        createdAt: '',
        updatedAt: '',
        version: 1,
      }),
    );
    api.ask.and.returnValue(
      of({
        answer: '<img src=x onerror=alert(1)> Quotes are valid for 30 days.',
        grounded: true,
        aiNarrativeAvailable: true,
        warnings: [],
        sources: [
          {
            documentId: 'doc-1',
            chunkId: 'chunk-1',
            title: 'Terms',
            chunkIndex: 0,
            score: 0.91,
            excerpt: 'Quotations remain valid for 30 days.',
          },
        ],
      }),
    );

    await TestBed.configureTestingModule({
      imports: [BusinessKnowledgePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BusinessKnowledgeApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BusinessKnowledgePageComponent);
    fixture.detectChanges();
  });

  it('loads documents and renders safe escaped answers with sources', () => {
    const component = fixture.componentInstance;
    component.question.setValue('What is our quotation validity?');
    component.ask();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(api.ask).toHaveBeenCalledWith('What is our quotation validity?');
    expect(text).toContain('<img src=x onerror=alert(1)> Quotes are valid for 30 days.');
    expect(text).toContain('Quotations remain valid for 30 days.');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });

  it('submits text ingestion and updates the document list', () => {
    const component = fixture.componentInstance;
    component.title.setValue('Terms');
    component.text.setValue('Payment is due within 15 days.');
    component.addText();

    expect(api.createText).toHaveBeenCalledWith('Terms', 'Payment is due within 15 days.');
    expect(component.documents().length).toBe(1);
  });
});
