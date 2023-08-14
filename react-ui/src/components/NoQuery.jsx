import React from 'react';
import { BiSearchAlt } from 'react-icons/bi';

const NoQuery = ({ infoText }) => {
  return (
    <div className="flex justify-center items-center w-full h-full flex-col fixed">
      <div className="flex bg-slate-200 rounded-lg">
        <BiSearchAlt className="text-slate-50 text-[10rem] m-6" />
      </div>
      <div className="flex mt-4 text-slate-500 font-light text-l">
        {infoText}
      </div>
    </div>
    // <div className="flex flex-col w-full h-full">
    //   <div className={`flex bg-slate-100 w-full h-full drop-shadow-md flex-col justify-center items-center`}>
    //     <div className="flex bg-slate-200 rounded-lg">
    //       <BiSearchAlt className="text-slate-50 text-[10rem] m-6" />
    //     </div>
    //     <div className="flex mt-4 text-slate-500 font-light text-l">
    //       {infoText}
    //     </div>
    //   </div>
    // </div>
  )
}

export default NoQuery