/**
 * @license
 * Copyright 2016-2018 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';
import { LabelConfig } from './../../../../shared/app-config.interface';
import { BaseControlValueAccessor } from './control-value-accessor.component';
import { Input, Output, EventEmitter, ChangeDetectorRef } from '@angular/core';
import { FormGroup, NgModel } from '@angular/forms';
import { Param } from '../../../../shared/app-config.interface';
import { PageService } from '../../../../services/page.service';
import { WebContentSvc } from '../../../../services/content-management.service';
import { GenericDomain } from '../../../../model/generic-domain.model';
import { ValidatorFn } from '@angular/forms/src/directives/validators';
import { ValidationUtils } from '../../validators/ValidationUtils';
import { ValidationConstraint } from './../../../../shared/validationconstraints.enum';
import { FormControl, AbstractControl } from '@angular/forms/src/model';
/**
 * \@author Dinakar.Meda
 * \@author Sandeep.Mantha
 * \@whatItDoes 
 * 
 * \@howToUse 
 * 
 */
export abstract class BaseControl<T> extends BaseControlValueAccessor<T> {
    @Input() element: Param;
    @Input() form: FormGroup;
    @Output() controlValueChanged =new EventEmitter();
    protected abstract model: NgModel;
    protected _elementStyle: string;
    public label: string;
    public helpText : string;
    inPlaceEditContext: any;
    showLabel: boolean = true;
    disabled: boolean;
    requiredCss:boolean = false;
    constructor(private pageService: PageService, private wcs: WebContentSvc, private cd: ChangeDetectorRef) {
        super();
    }

    setState(event:any,frmInp:any) {
        frmInp.element.leafState = event;
        this.cd.markForCheck();
        //console.log(frmInp.element.leafState);
    }

    emitValueChangedEvent(formControl:any,$event:any) {
        if (this.inPlaceEditContext) {
            this.inPlaceEditContext.value = formControl.value;
        }
        this.controlValueChanged.emit(formControl.element);
    }

    ngOnInit() {
        
        this.value = this.element.leafState;
        this.disabled = !this.element.enabled;
        let labelContent: LabelConfig = this.wcs.findLabelContent(this.element);
        this.label = labelContent.text;
        this.helpText = labelContent.helpText;
        this.requiredCss = ValidationUtils.applyelementStyle(this.element);
        if (this.form) {
            let frmCtrl = this.form.controls[this.element.config.code];
            //rebind the validations as there are dynamic validations along with the static validations
            if(frmCtrl!=null && this.element.activeValidationGroups != null && this.element.activeValidationGroups.length > 0) {
                this.requiredCss = ValidationUtils.rebindValidations(frmCtrl,this.element.activeValidationGroups,this.element);
            } 
        }
    }

    ngAfterViewInit(){
        if(this.form!= undefined && this.form.controls[this.element.config.code]!= null) {
            this.form.controls[this.element.config.code].valueChanges.subscribe(($event) => this.setState($event,this));
            this.stateUpdateSubscriber();
            this.validationUpdateSubscriber();
        }
        this.onChangeEventSubscriber();
    }

    private stateUpdateSubscriber() {
        this.pageService.eventUpdate$.subscribe(event => {
            let frmCtrl = this.form.controls[event.config.code];
            if(frmCtrl!=null && event.path == this.element.path) {
                if(event.leafState!=null){
                    if (event.alias === 'Calendar') {
                        event.leafState= new Date(event.leafState);
                      }
                    frmCtrl.setValue(event.leafState);
                } else
                    frmCtrl.reset();
            }
        });
    }

    private validationUpdateSubscriber() {
        this.pageService.validationUpdate$.subscribe(event => {
            let frmCtrl = this.form.controls[event.config.code];
            if(frmCtrl!=null) {
                if(event.path === this.element.path) {
                    //bind dynamic validations on a param as a result of a state change of another param
                    if(event.activeValidationGroups != null && event.activeValidationGroups.length > 0) {
                        this.requiredCss = ValidationUtils.rebindValidations(frmCtrl,event.activeValidationGroups,this.element);
                    } else {
                        this.requiredCss = ValidationUtils.applyelementStyle(this.element);
                        var staticChecks: ValidatorFn[] = [];
                        staticChecks = ValidationUtils.buildStaticValidations(this.element);
                        frmCtrl.setValidators(staticChecks);
                    }
                    ValidationUtils.assessControlValidation(event,frmCtrl);
                    this.disabled = !event.enabled;   
                }

            }
        });
    }

    private onChangeEventSubscriber() {
        this.controlValueChanged.subscribe(($event) => {
            //console.log($event);
            if ($event.config.uiStyles.attributes.postEventOnChange) {
               this.pageService.postOnChange($event.path, 'state', JSON.stringify($event.leafState));
            } else if($event.config.uiStyles.attributes.postButtonUrl) {
               let item: GenericDomain = new GenericDomain();
               this.pageService.processPost(this.element.config.uiStyles.attributes.postButtonUrl, null, $event.leafState, 'POST');
            }
        });
    }
    /** invoked from InPlaceEdit control */
    setInPlaceEditContext(context: any) {
        this.showLabel = false;
        this.inPlaceEditContext = context;
    }
    /**
     * The hidden attribute for this param
     */
    public get hidden(): boolean {
        return this.element.config.uiStyles.attributes.hidden;
    }

    /**
     * The help attribute for this param
     */
    public get help(): string {
        return this.element.config.uiStyles.attributes.help;
    }

    /**
     * The help readOnly for this param
     */
    public get readOnly(): boolean {
        return this.element.config.uiStyles.attributes.readOnly;
    }

    /**
     * The type attribute for this param
     */
    public get type(): string {
        return this.element.config.uiStyles.attributes.type;
    }
}
